package no.nav.reops

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ControlAdvice {

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<String> {
        LOG.debug(ex.toString())
        return ResponseEntity("Noe uventet feilet", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(ControlAdvice::class.java)
    }
}