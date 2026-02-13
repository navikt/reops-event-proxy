package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap

@CrossOrigin(value = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
@RestController
class EventController(
    private val eventPublishService: EventPublishService, private val meterRegistry: MeterRegistry
) {
    private val receivedRequests: Counter = meterRegistry.counter("requests_total", "result", "recieved")

    private val truncCounters = ConcurrentHashMap<String, Counter>()
    private fun truncCounter(field: String): Counter = truncCounters.computeIfAbsent(field) {
        meterRegistry.counter("truncations_by_field_total", "field", field)
    }

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody eventMono: Mono<Event>,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?,
        @RequestHeader(FORWARDED_FOR, required = false) forwardedFor: String?,
    ): Mono<ResponseEntity<Response>> {
        receivedRequests.increment()

        val safeUserAgent = userAgent?.trim().orEmpty()
        val safeExcludeFilters = excludeFilters?.trim().takeUnless { it.isNullOrEmpty() }
        val safeForwardedFor = forwardedFor?.trim().takeUnless { it.isNullOrEmpty() }

        return eventMono.publishOn(Schedulers.boundedElastic()).map { event ->
            val sanitized = event.sanitizeForKafkaWithReport()
            LOG.info("Received event website={}", event.payload.website)
            recordTruncationMetrics(sanitized.truncationReport)

            eventPublishService.publishEventAsync(
                event = sanitized.event,
                userAgent = safeUserAgent,
                excludeFilters = safeExcludeFilters,
                forwardedFor = safeForwardedFor
            )

            ResponseEntity.status(HttpStatus.CREATED).body(Response("Created", 201, sanitized.truncationReport))
        }
    }

    private fun recordTruncationMetrics(report: TruncationReport?) {
        report?.violations?.asSequence()?.map { it.field }?.distinct()?.forEach { truncCounter(it).increment() }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventController::class.java)
    }
}

data class Response(
    val message: String, val code: Int, val truncationReport: TruncationReport? = null
)