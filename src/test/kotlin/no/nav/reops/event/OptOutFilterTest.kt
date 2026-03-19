package no.nav.reops.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OptOutFilterTest {

    @Test
    fun `parseHeader returns null for null input`() {
        assertNull(OptOutFilter.parseHeader(null))
    }

    @Test
    fun `parseHeader returns null for blank input`() {
        assertNull(OptOutFilter.parseHeader(""))
        assertNull(OptOutFilter.parseHeader("   "))
    }

    @Test
    fun `parseHeader parses single known filter`() {
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("uuid"))
    }

    @Test
    fun `parseHeader is case-insensitive`() {
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("UUID"))
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("Uuid"))
    }

    @Test
    fun `parseHeader trims whitespace around tokens`() {
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("  uuid  "))
    }

    @Test
    fun `parseHeader deduplicates filters`() {
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("uuid,uuid"))
    }

    @Test
    fun `parseHeader ignores unknown tokens`() {
        assertEquals(listOf(OptOutFilter.UUID), OptOutFilter.parseHeader("uuid,unknown"))
    }

    @Test
    fun `parseHeader returns null when all tokens are unknown`() {
        assertNull(OptOutFilter.parseHeader("unknown,other"))
    }

    @Test
    fun `parseHeader rejects header exceeding 50 characters`() {
        val longValue = "a".repeat(51)
        val ex = assertThrows<IllegalArgumentException> {
            OptOutFilter.parseHeader(longValue)
        }
        assertEquals("x-opt-out-filters header exceeds 50 characters", ex.message)
    }

    @Test
    fun `parseHeader accepts header at exactly 50 characters`() {
        val value = "a".repeat(50)
        assertNull(OptOutFilter.parseHeader(value))
    }
}

