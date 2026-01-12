package no.nav.reops.event

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
class Controller(private val eventPublishService: EventPublishService) {
    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT, required = true) userAgent: String
    ): CompletableFuture<ResponseEntity<Response>> {
        return eventPublishService.publishEventAsync(event, userAgent)
            .thenApply {
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(Response(melding = "Created", kode = 201))
            }
    }
}

data class Response(
    val melding: String,
    val kode: Int
)