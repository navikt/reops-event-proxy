package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@CrossOrigin(value = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
@RestController
class EventController(
    private val eventPublishService: EventPublishService, private val meterRegistry: MeterRegistry
) {

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?,
        @RequestHeader(FORWARDED_FOR, required = false) forwardedFor: String?,
    ): ResponseEntity<Response> {
        val sanitized = event.sanitizeForKafkaWithReport()
        recordTruncationMetrics(sanitized.truncationReport)

        val safeUserAgent = userAgent?.trim().takeUnless { it.isNullOrEmpty() } ?: ""
        val safeExcludeFilters = excludeFilters?.trim().takeUnless { it.isNullOrEmpty() }
        val safeForwardedFor = forwardedFor?.trim().takeUnless { it.isNullOrEmpty() }

        eventPublishService.publishEventAsync(
            event = sanitized.event,
            userAgent = safeUserAgent,
            excludeFilters = safeExcludeFilters,
            forwardedFor = safeForwardedFor
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            Response("Created", 201, sanitized.truncationReport)
        )
    }

    private fun recordTruncationMetrics(report: TruncationReport?) {
        report?.violations?.map { it.field }?.distinct()?.forEach { field ->
            Counter.builder("truncations_by_field_total").tag("field", field).register(meterRegistry).increment()
        }
    }
}

data class Response(
    val message: String, val code: Int, val truncationReport: TruncationReport? = null
)
