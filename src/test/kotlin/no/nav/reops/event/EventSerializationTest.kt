package no.nav.reops.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EventSerializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `omits null id field`() {
        val event = Event(
            type = "pageview",
            payload = Event.Payload(website = UUID.randomUUID())
        )

        val json = mapper.writeValueAsString(event)

        assertFalse(json.contains("\"id\""))
    }

    @Test
    fun `includes id field when present`() {
        val event = Event(
            type = "pageview",
            payload = Event.Payload(website = UUID.randomUUID(), id = "abc-123")
        )

        val json = mapper.writeValueAsString(event)

        assertTrue(json.contains("\"id\":\"abc-123\""))
    }
}

