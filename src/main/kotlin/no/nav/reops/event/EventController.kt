package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import tools.jackson.module.kotlin.jacksonObjectMapper

@CrossOrigin(value = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
@RestController
class EventController(
    private val eventPublishService: EventPublishService, private val meterRegistry: MeterRegistry
) {
    private val receivedRequests: Counter = meterRegistry.counter("requests_total", "result", "recieved")

    private val scriptVersionPresent: Counter = meterRegistry.counter("script_version_total", "status", "present")
    private val scriptVersionMissing: Counter = meterRegistry.counter("script_version_total", "status", "missing")

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
        @RequestHeader(SCRIPT_VERSION, required = false) scriptVersion: String?
    ): Mono<ResponseEntity<Response>> = processEvent(eventMono, userAgent, optOutFilters, forwardedFor, scriptVersion)

    @PostMapping("/api/send", consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun sendEventText(
        @RequestBody bodyMono: Mono<String>,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(OPT_OUT_FILTERS, required = false) optOutFilters: String?,
        @RequestHeader(FORWARDED_FOR, required = false) forwardedFor: String?,
        @RequestHeader(SCRIPT_VERSION, required = false) scriptVersion: String?
    ): Mono<ResponseEntity<Response>> =
        processEvent(bodyMono.map { objectMapper.readValue(it, Event::class.java) }, userAgent, optOutFilters, forwardedFor, scriptVersion)

    private fun processEvent(
        eventMono: Mono<Event>,
        userAgent: String?,
        optOutFilters: String?,
        forwardedFor: String?,
        scriptVersion: String?
    ): Mono<ResponseEntity<Response>> {
        receivedRequests.increment()

        val safeUserAgent = userAgent?.trim().orEmpty()
        val safeOptOutFilters = OptOutFilter.parseHeader(optOutFilters)
        val safeForwardedFor = forwardedFor?.trim().takeUnless { it.isNullOrEmpty() }
        val safeScriptVersion = scriptVersion?.trim().takeUnless { it.isNullOrEmpty() }

        if (safeScriptVersion != null) scriptVersionPresent.increment() else scriptVersionMissing.increment()

        return eventMono.flatMap { event ->
            val sanitized = event.sanitizeForKafkaWithReport()
            LOG.info("Received event website={} script={}", event.payload.website, safeScriptVersion)
            recordTruncationMetrics(sanitized.truncationReport)

            Mono.fromCompletionStage(
                eventPublishService.publishEventAsync(
                    event = sanitized.event,
                    userAgent = safeUserAgent,
                    optOutFilters = safeOptOutFilters,
                    forwardedFor = safeForwardedFor
                )
            ).map {
                ResponseEntity.status(HttpStatus.CREATED).body(Response("Created", 201, sanitized.truncationReport))
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
    val message: String, val code: Int, val truncationReport: TruncationReport? = null
)