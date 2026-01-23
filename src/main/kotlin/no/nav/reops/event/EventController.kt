package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@CrossOrigin(value = ["*"], allowedHeaders = ["*"], methods = [RequestMethod.POST, RequestMethod.OPTIONS])
@RestController
class Controller(
    private val eventPublishService: EventPublishService, private val meterRegistry: MeterRegistry
) {

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?,
        @RequestHeader(X_CLIENT_REGION, required = false) clientRegion: String?
    ): CompletableFuture<ResponseEntity<Response>> {
        val sanitized = event.sanitizeForKafkaWithReport()
        recordTruncationMetrics(sanitized.truncationReport)

        val safeUserAgent = userAgent?.takeIf { it.isNotBlank() } ?: ""
        val safeExcludeFilters = excludeFilters?.takeIf { it.isNotBlank() }
        val safeClientRegion = clientRegion?.takeIf { it.isNotBlank() } ?: ""

        return eventPublishService.publishEventAsync(
            sanitized.event, safeUserAgent, safeExcludeFilters, safeClientRegion
        ).thenApply {
            ResponseEntity.status(HttpStatus.CREATED).body(
                Response("Created", 201, sanitized.truncationReport)
            )
        }
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