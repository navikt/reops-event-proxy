package no.nav.reops

import no.nav.reops.exception.InvalidEventException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ServerWebInputException
import tools.jackson.databind.exc.InvalidFormatException

@ControllerAdvice
class ControlAdvice {

    @ExceptionHandler(InvalidEventException::class)
    fun handleInvalidEvent(ex: InvalidEventException): ResponseEntity<ErrorResponse> {
        LOG.debug("Invalid event: {}", ex.message)
        val body = ErrorResponse(
            error = "INVALID_EVENT",
            message = ex.message ?: "Invalid event payload",
            status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleDecodingException(ex: ServerWebInputException): ResponseEntity<ErrorResponse> {
        val message = "Invalid format in request body"
        LOG.debug("Invalid format: {}", ex.message)
        val body = ErrorResponse(
            error = "INVALID_FORMAT", message = message, status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        LOG.error("Unexpected error", ex)
        val body = ErrorResponse(
            error = "INTERNAL_ERROR", message = "Unexpected error", status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(ControlAdvice::class.java)
    }
}

data class ErrorResponse(
    val error: String, val message: String, val status: Int
)