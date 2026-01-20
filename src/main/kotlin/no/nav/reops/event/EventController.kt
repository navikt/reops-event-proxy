package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.truncation.TruncationReport
import no.nav.reops.truncation.sanitizeForKafkaWithReport
import no.nav.reops.validate.ValidateEvent
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

const val USER_AGENT = "User-Agent"
const val EXCLUDE_FILTERS = "X-Exclude-Filters"

@CrossOrigin(
    value = ["*"],
    allowedHeaders = ["*"],
    methods = [RequestMethod.POST, RequestMethod.OPTIONS],
    maxAge = 8000
)
@RestController
class Controller(
    private val eventPublishService: EventPublishService,
    private val meterRegistry: MeterRegistry,
    private val validateEvent: ValidateEvent = ValidateEvent()
) {

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT, required = false) userAgent: String?,
        @RequestHeader(EXCLUDE_FILTERS, required = false) excludeFilters: String?
    ): CompletableFuture<ResponseEntity<Response>> {
        validateEvent.validate(event)

        val sanitized = event.sanitizeForKafkaWithReport()
        recordTruncationMetrics(sanitized.truncationReport)

        LOG.info("excludedFilters: $excludeFilters")
        val ua = userAgent ?: "unknown"

        return eventPublishService.publishEventAsync(sanitized.event, ua, excludeFilters)
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