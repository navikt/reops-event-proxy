package no.nav.reops.validate

import no.nav.reops.event.Event
import no.nav.reops.exception.InvalidEventException
import java.util.UUID

class ValidateEvent {

    fun validate(event: Event) {
        validateWebsiteUUID(event.payload.website)
        validatePayloadData(event)
    }

    private fun validateWebsiteUUID(website: String) {
        try {
            UUID.fromString(website)
        } catch (_: IllegalArgumentException) {
            throw InvalidEventException(
                "payload.website must be a valid UUID, but was '$website'"
            )
        }
    }

    private fun validatePayloadData(event: Event) {
        val data = event.payload.data ?: return
        if (!data.isObject && !data.isArray) {
            throw InvalidEventException(
                "payload.data must be a JSON object or array, but was ${data.nodeType}"
            )
        }
    }
}