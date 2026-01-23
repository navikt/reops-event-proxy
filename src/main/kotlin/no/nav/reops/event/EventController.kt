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
        @RequestHeader headers: Map<String, String>,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?,
        @RequestHeader(X_CLIENT_REGION, required = false) clientRegion: String?,
        @RequestHeader(X_CLIENT_CITY, required = false) clientCity: String?
    ): CompletableFuture<ResponseEntity<Response>> {
        val sanitized = event.sanitizeForKafkaWithReport()
        recordTruncationMetrics(sanitized.truncationReport)

        headers.forEach { (key, value) ->
            LOG.info("$key: $value")
        }

        val safeUserAgent = userAgent?.trim().takeUnless { it.isNullOrEmpty() } ?: ""
        val safeExcludeFilters = excludeFilters?.trim().takeUnless { it.isNullOrEmpty() }
        val safeClientRegion = clientRegion?.trim().takeUnless { it.isNullOrEmpty() }
        val safeClientCity = clientCity?.trim().takeUnless { it.isNullOrEmpty() }

        return eventPublishService.publishEventAsync(
            event = sanitized.event,
            userAgent = safeUserAgent,
            excludeFilters = safeExcludeFilters,
            clientRegion = safeClientRegion,
            clientCity = safeClientCity
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

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventController::class.java)
    }
}

data class Response(
    val message: String, val code: Int, val truncationReport: TruncationReport? = null
)
