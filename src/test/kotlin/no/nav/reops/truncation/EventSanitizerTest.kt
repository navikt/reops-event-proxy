package no.nav.reops.truncation

import no.nav.reops.event.Event
import no.nav.reops.exception.InvalidEventException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.JsonNodeFactory
import java.util.UUID

class EventSanitizerTest {

    private val f = JsonNodeFactory.instance

    @Test
    fun `throws when type is blank (after trim)`() {
        val event = Event(
            type = "   ", payload = Event.Payload(website = UUID.randomUUID())
        )

        val ex = assertThrows(InvalidEventException::class.java) {
            event.sanitizeForKafkaWithReport()
        }

        assertTrue(ex.message!!.contains("type must not be blank"))
    }

    @Test
    fun `trims type and converts blank optional fields to null and trims non-blank optionals`() {
        val event = Event(
            type = "  pageview  ", payload = Event.Payload(
                website = UUID.randomUUID(), hostname = "   ",             // blank -> null
                screen = "\n\t",              // blank -> null
                language = "  nb  ",          // trim -> "nb"
                title = "",                   // blank -> null
                url = " https://example.com ",// trim -> "https://example.com"
                referrer = "   ",             // blank -> null
                name = "   ",                 // blank -> null
                data = null
            )
        )

        val sanitized = event.sanitizeForKafkaWithReport()

        assertEquals("pageview", sanitized.event.type)

        val p = sanitized.event.payload
        assertNull(p.hostname)
        assertNull(p.screen)
        assertNull(p.title)
        assertNull(p.referrer)
        assertNull(p.name)

        assertEquals("nb", p.language)
        assertEquals("https://example.com", p.url)

        assertNull(sanitized.truncationReport)
    }

    @Test
    fun `normalizes primitive data into object value`() {
        val website = UUID.randomUUID()

        val eventNumber = Event(
            type = "t", payload = Event.Payload(website = website, data = f.numberNode(123))
        )
        val sanitizedNumber = eventNumber.sanitizeForKafkaWithReport().event
        assertTrue(sanitizedNumber.payload.data!!.isObject)
        assertEquals(123, sanitizedNumber.payload.data.get("value").asInt())

        val eventBool = Event(
            type = "t", payload = Event.Payload(website = website, data = f.booleanNode(true))
        )
        val sanitizedBool = eventBool.sanitizeForKafkaWithReport().event
        assertTrue(sanitizedBool.payload.data!!.isObject)
        assertTrue(sanitizedBool.payload.data.get("value").asBoolean())

        val eventString = Event(
            type = "t", payload = Event.Payload(website = website, data = f.stringNode("abc"))
        )
        val sanitizedString = eventString.sanitizeForKafkaWithReport().event
        assertTrue(sanitizedString.payload.data!!.isObject)
        assertEquals("abc", sanitizedString.payload.data.get("value").asString())
    }

    @Test
    fun `does not wrap object or array data`() {
        val website = UUID.randomUUID()

        val obj = f.objectNode().put("k", "v")
        val eventObj = Event("t", Event.Payload(website = website, data = obj))
        val sanitizedObj = eventObj.sanitizeForKafkaWithReport().event
        assertTrue(sanitizedObj.payload.data!!.isObject)
        assertEquals("v", sanitizedObj.payload.data.get("k").asString())

        val arr = f.arrayNode().add("x").add("y")
        val eventArr = Event("t", Event.Payload(website = website, data = arr))
        val sanitizedArr = eventArr.sanitizeForKafkaWithReport().event
        assertTrue(sanitizedArr.payload.data!!.isArray)
        assertEquals("x", sanitizedArr.payload.data[0].asString())
        assertEquals("y", sanitizedArr.payload.data[1].asString())
    }

    @Test
    fun `truncates long fields and produces a report with correct paths`() {
        val website = UUID.randomUUID()
        val long600 = "a".repeat(600)
        val long700 = "b".repeat(700)

        val data = f.objectNode().apply {
            set("value", f.stringNode(long700))
            set(
                "arr", f.arrayNode().add(f.stringNode("ok")).add(f.stringNode(long600))
            )
        }

        val event = Event(
            type = "x".repeat(600), payload = Event.Payload(
                website = website, title = long600, data = data
            )
        )

        val result = event.sanitizeForKafkaWithReport()
        val sanitized = result.event
        val report = result.truncationReport

        assertEquals(MAX_LENGTH, sanitized.type.length)
        assertTrue(sanitized.type.endsWith("TRUNCATED"))

        assertNotNull(sanitized.payload.title)
        assertEquals(MAX_LENGTH, sanitized.payload.title!!.length)
        assertTrue(sanitized.payload.title.endsWith("TRUNCATED"))

        val sanitizedValue = sanitized.payload.data!!.get("value").asString()
        assertEquals(MAX_LENGTH, sanitizedValue.length)
        assertTrue(sanitizedValue.endsWith("TRUNCATED"))

        val sanitizedArr1 = sanitized.payload.data.get("arr")[1].asString()
        assertEquals(MAX_LENGTH, sanitizedArr1.length)
        assertTrue(sanitizedArr1.endsWith("TRUNCATED"))

        assertNotNull(report)
        assertEquals(MAX_LENGTH, report!!.limit)

        val byField = report.violations.associateBy { it.field }
        assertEquals(600, byField["type"]?.length)
        assertEquals(600, byField["payload.title"]?.length)
        assertEquals(700, byField["payload.data.value"]?.length)
        assertEquals(600, byField["payload.data.arr[1]"]?.length)
    }
}