package no.nav.reops.truncation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.JsonNodeFactory

class TruncationValidateTest {

    private val nodeFactory = JsonNodeFactory.instance

    @Test
    fun `truncateMarked returns input when within limit and no report`() {
        val tv = TruncationValidate(limit = 10)
        val out = tv.truncateMarked("field", "abc")
        assertEquals("abc", out)
        assertNull(tv.reportOrNull())
    }

    @Test
    fun `truncateMarked truncates and appends suffix and reports original length`() {
        val tv = TruncationValidate(limit = 10) // suffix=9 => keep=1
        val out = tv.truncateMarked("field", "0123456789ABCDE") // len 15

        assertEquals(10, out.length)
        assertTrue(out.endsWith("TRUNCATED"))
        assertEquals("0TRUNCATED", out)

        val report = tv.reportOrNull()
        assertNotNull(report)
        assertEquals(10, report!!.limit)
        assertEquals(1, report.violations.size)
        assertEquals("field", report.violations[0].field)
        assertEquals(15, report.violations[0].length)
    }

    @Test
    fun `truncateJsonNode traverses objects and arrays and truncates only strings`() {
        val tv = TruncationValidate(limit = 12) // keep=3 (12-9)

        val node = nodeFactory.objectNode().apply {
            put("ok", "hey")
            put("long", "abcdefghijklmno") // len 15 -> trunc
            set(
                "arr", nodeFactory.arrayNode().add(nodeFactory.numberNode(123)).add(nodeFactory.stringNode("abcdefghijklmnop")) // trunc
            )
            set(
                "nested", nodeFactory.objectNode().put("s", "abcdefghijklmnop") // trunc
            )
        }

        val out = tv.truncateJsonNode("root", node)!!

        assertEquals("hey", out.get("ok").asString())
        assertEquals(12, out.get("long").asString().length)
        assertTrue(out.get("long").asString().endsWith("TRUNCATED"))

        assertTrue(out.get("arr")[0].isNumber)
        assertEquals(123, out.get("arr")[0].asInt())
        assertEquals(12, out.get("arr")[1].asString().length)
        assertTrue(out.get("arr")[1].asString().endsWith("TRUNCATED"))

        assertEquals(12, out.get("nested").get("s").asString().length)
        assertTrue(out.get("nested").get("s").asString().endsWith("TRUNCATED"))

        val report = tv.reportOrNull()!!
        val fields = report.violations.map { it.field }.toSet()

        assertTrue("root.long" in fields)
        assertTrue("root.arr[1]" in fields)
        assertTrue("root.nested.s" in fields)
    }
}