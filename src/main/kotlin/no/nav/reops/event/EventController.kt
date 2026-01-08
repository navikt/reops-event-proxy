package no.nav.reops.event

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.ProducerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class Controller(private val eventPublishService: EventPublishService) {

    @PostMapping("/api/send")
    fun sendEvent(
        @RequestBody event: Event,
        @RequestHeader(USER_AGENT, required = true) userAgent: String
    ): Mono<ResponseEntity<Response>> {
        return Mono.fromFuture(eventPublishService.publishEventAsync(event, userAgent))
            .map { ResponseEntity.status(HttpStatus.CREATED).body(Response(melding = "Created", kode = 201)) }
            .onErrorResume {
                Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Response(melding = "Could not recieve, internal error", kode = 503)))
            }
    }
}

data class Response(
    val melding: String,
    val kode: Int
)