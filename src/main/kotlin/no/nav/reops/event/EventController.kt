package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.session.SessionResolver
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import tools.jackson.module.kotlin.jacksonObjectMapper

const val UMAMI_CACHE_HEADER = "x-umami-cache"

@CrossOrigin(value = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
@RestController
class EventController(
    private val eventPublishService: EventPublishService,
    private val sessionResolver: SessionResolver,
    private val meterRegistry: MeterRegistry
) {
    private val receivedRequests: Counter = meterRegistry.counter("requests_total", "result", "recieved")

    private val scriptVersionPresent: Counter = meterRegistry.counter("script_version_total", "status", "present")
    private val scriptVersionMissing: Counter = meterRegistry.counter("script_version_total", "status", "missing")
    private val optOutPresent: Counter = meterRegistry.counter("opt_out_total", "status", "present")
    private val optOutMissing: Counter = meterRegistry.counter("opt_out_total", "status", "missing")
    private val cachePresent: Counter = meterRegistry.counter("umami_cache_total", "status", "present")
    private val cacheMissing: Counter = meterRegistry.counter("umami_cache_total", "status", "missing")

    private val truncCounters: Map<String, Counter> = KNOWN_FIELDS.associateWith { field ->
        meterRegistry.counter("truncations_by_field_total", "field", field)
    }

    private val objectMapper = jacksonObjectMapper()

    private fun truncCounter(field: String): Counter = truncCounters[field] ?: truncCounters.getValue(DATA_BUCKET)

    @PostMapping("/api/send", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun sendEventJson(
        @RequestBody eventMono: Mono<Event>,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(OPT_OUT_FILTERS, required = false) optOutFilters: String?,
        @RequestHeader(FORWARDED_FOR, required = false) forwardedFor: String?,
        @RequestHeader(SCRIPT_VERSION, required = false) scriptVersion: String?,
        @RequestHeader(UMAMI_CACHE_HEADER, required = false) umamiCache: String?
    ): Mono<ResponseEntity<Response>> =
        processEvent(eventMono, userAgent, optOutFilters, forwardedFor, scriptVersion, umamiCache)

    @PostMapping("/api/send", consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun sendEventText(
        @RequestBody bodyMono: Mono<String>,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(OPT_OUT_FILTERS, required = false) optOutFilters: String?,
        @RequestHeader(FORWARDED_FOR, required = false) forwardedFor: String?,
        @RequestHeader(SCRIPT_VERSION, required = false) scriptVersion: String?,
        @RequestHeader(UMAMI_CACHE_HEADER, required = false) umamiCache: String?
    ): Mono<ResponseEntity<Response>> =
        processEvent(
            bodyMono.map { objectMapper.readValue(it, Event::class.java) },
            userAgent, optOutFilters, forwardedFor, scriptVersion, umamiCache
        )

    private fun processEvent(
        eventMono: Mono<Event>,
        userAgent: String?,
        optOutFilters: String?,
        forwardedFor: String?,
        scriptVersion: String?,
        umamiCache: String?
    ): Mono<ResponseEntity<Response>> {
        receivedRequests.increment()

        val safeUserAgent = userAgent?.trim().orEmpty()
        val safeOptOutFilters = OptOutFilter.parseHeader(optOutFilters)
        val safeForwardedFor = forwardedFor?.trim().takeUnless { it.isNullOrEmpty() }
        val safeScriptVersion = scriptVersion?.trim().takeUnless { it.isNullOrEmpty() }
        val safeUmamiCache = umamiCache?.trim().takeUnless { it.isNullOrEmpty() }

        if (safeScriptVersion != null) scriptVersionPresent.increment() else scriptVersionMissing.increment()
        if (optOutFilters != null) optOutPresent.increment() else optOutMissing.increment()
        if (safeUmamiCache != null) cachePresent.increment() else cacheMissing.increment()

        return eventMono.flatMap { event ->
            val sanitized = event.sanitizeForKafkaWithReport()
            recordTruncationMetrics(sanitized.truncationReport)

            val ip = safeForwardedFor?.split(",")?.firstOrNull()?.trim()
            val resolved = sessionResolver.resolve(
                websiteId = sanitized.event.payload.website,
                ip = ip,
                userAgent = safeUserAgent,
                identity = sanitized.event.payload.id,
                incomingCacheToken = safeUmamiCache
            )

            LOG.info(
                "Received event website={} script={} sessionId={} visitId={} cacheHit={}",
                sanitized.event.payload.website, safeScriptVersion,
                resolved.sessionId, resolved.visitId, safeUmamiCache != null
            )

            Mono.fromCompletionStage(
                eventPublishService.publishEventAsync(
                    event = sanitized.event,
                    userAgent = safeUserAgent,
                    optOutFilters = safeOptOutFilters,
                    forwardedFor = safeForwardedFor,
                    sessionId = resolved.sessionId,
                    visitId = resolved.visitId
                )
            ).map {
                ResponseEntity.status(HttpStatus.CREATED).body(
                    Response(
                        message = "Created",
                        code = 201,
                        truncationReport = sanitized.truncationReport,
                        cache = resolved.cacheToken,
                        sessionId = resolved.sessionId.toString(),
                        visitId = resolved.visitId.toString()
                    )
                )
            }
        }
    }

    private fun recordTruncationMetrics(report: TruncationReport?) {
        report?.violations?.asSequence()?.map { it.field }?.distinct()?.forEach { truncCounter(it).increment() }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventController::class.java)
        private const val DATA_BUCKET = "payload.data"
        private val KNOWN_FIELDS = listOf(
            "payload.hostname",
            "payload.screen",
            "payload.language",
            "payload.title",
            "payload.url",
            "payload.referrer",
            "payload.name",
            DATA_BUCKET,
        )
    }
}

data class Response(
    val message: String,
    val code: Int,
    val truncationReport: TruncationReport? = null,
    val cache: String? = null,
    val sessionId: String? = null,
    val visitId: String? = null
)
