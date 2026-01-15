package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.exception.InvalidEventException
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

const val USER_AGENT = "User-Agent"
const val EXCLUDE_FILTERS = "X-Exclude-Filters"

@RestController
class Controller(
    private val eventPublishService: EventPublishService,
    private val meterRegistry: MeterRegistry
) {

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT) userAgent: String,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?
    ): CompletableFuture<ResponseEntity<Response>> {

        validateUmamiPayload(event)

        val sanitized = event.sanitizeForKafkaWithReport()
        recordTruncationMetrics(sanitized.truncationReport)

        LOG.info("excludedFilters: $excludeFilters")

        return eventPublishService.publishEventAsync(sanitized.event, userAgent, excludeFilters)
            .thenApply {
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(
                        Response(
                            message = "Created",
                            code = 201,
                            truncationReport = sanitized.truncationReport
                        )
                    )
            }
    }

    private fun validateUmamiPayload(event: Event) {
        val data = event.payload.data ?: return

        if (!data.isObject && !data.isArray) {
            throw InvalidEventException(
                "payload.data must be a JSON object or array, but was ${data.nodeType}"
            )
        }
    }

    private fun recordTruncationMetrics(report: TruncationReport?) {
        if (report == null) return
        report.violations
            .map { it.field }
            .distinct()
            .forEach { field ->
                Counter.builder("truncations_by_field_total")
                    .tag("field", field)
                    .register(meterRegistry)
                    .increment()
            }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(Controller::class.java)
    }
}

data class Response(
    val message: String,
    val code: Int,
    val truncationReport: TruncationReport? = null
)