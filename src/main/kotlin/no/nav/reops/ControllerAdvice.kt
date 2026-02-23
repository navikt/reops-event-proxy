package no.nav.reops

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.exception.InvalidEventException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException

@ControllerAdvice
class ControlAdvice(meterRegistry: MeterRegistry) {

    private val failedRequests: Counter = meterRegistry.counter("requests_total", "result", "failure")

    @ExceptionHandler(InvalidEventException::class)
    fun handleInvalidEvent(ex: InvalidEventException): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        LOG.info("Invalid event: {}", ex.message)
        val body = ErrorResponse(
            error = "INVALID_EVENT",
            message = ex.message ?: "Invalid event payload",
            status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleDecodingException(ex: ServerWebInputException): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        // Log only the exception class and a sanitized reason — never the raw message
        // which may contain deserialized field values from the request body
        val reason = sanitizeMessage(ex)
        LOG.info("Invalid request body: {}", reason)
        val body = ErrorResponse(
            error = "INVALID_FORMAT",
            message = "Invalid format in request body",
            status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(UnsupportedMediaTypeStatusException::class)
    fun handleUnsupportedMediaType(ex: UnsupportedMediaTypeStatusException): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        LOG.info("Unsupported media type: {}", ex.contentType)
        val body = ErrorResponse(
            error = "UNSUPPORTED_MEDIA_TYPE",
            message = "Content-Type must be application/json",
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
        )
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body)
    }

    @ExceptionHandler(MethodNotAllowedException::class)
    fun handleMethodNotAllowed(ex: MethodNotAllowedException): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        LOG.info("Method not allowed: {}", ex.httpMethod)
        val body = ErrorResponse(
            error = "METHOD_NOT_ALLOWED",
            message = "HTTP method ${ex.httpMethod} is not supported",
            status = HttpStatus.METHOD_NOT_ALLOWED.value()
        )
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        val status = HttpStatus.valueOf(ex.statusCode.value())
        LOG.info("Response status exception: {} {}", status.value(), status.reasonPhrase)
        val body = ErrorResponse(
            error = status.reasonPhrase.uppercase().replace(' ', '_'),
            message = status.reasonPhrase,
            status = status.value()
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        failedRequests.increment()
        // Log the exception type but never the full message — it may contain event payload data
        LOG.error("Unexpected error [{}]: {}", ex.javaClass.simpleName, sanitizeMessage(ex))
        val body = ErrorResponse(
            error = "INTERNAL_ERROR",
            message = "Unexpected error",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(ControlAdvice::class.java)

        /** Max characters to include from an exception message in logs to avoid leaking payload data. */
        private const val MAX_LOG_MESSAGE_LENGTH = 200

        /**
         * Returns a safe, truncated summary of an exception for logging.
         * Only includes the exception class name and a length-limited prefix of the message,
         * which avoids accidentally logging full event field values that Jackson or Spring
         * may embed in exception messages.
         */
        private fun sanitizeMessage(ex: Exception): String {
            val className = ex.javaClass.simpleName
            val msg = ex.message?.take(MAX_LOG_MESSAGE_LENGTH) ?: "no message"
            return "$className: $msg"
        }
    }
}

data class ErrorResponse(
    val error: String, val message: String, val status: Int
)
