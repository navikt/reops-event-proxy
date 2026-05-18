package no.nav.reops.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class UmamiCryptoTest {

    @Test
    fun `hash matches upstream Umami sha512 hex with empty-string-joined args`() {
        val actual = UmamiCrypto.hash("a", "b", "c")
        val expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
        assertEquals(expected, actual)
    }

    @Test
    fun `hash is deterministic for same input ordering`() {
        val a = UmamiCrypto.hash("foo", "bar", "baz")
        val b = UmamiCrypto.hash("foo", "bar", "baz")
        assertEquals(a, b)
    }

    @Test
    fun `hash is order-sensitive`() {
        val a = UmamiCrypto.hash("foo", "bar")
        val b = UmamiCrypto.hash("bar", "foo")
        assertNotEquals(a, b)
    }

    @Test
    fun `nameBasedUuid is deterministic and DNS-namespaced`() {
        val secret = UmamiCrypto.derivedSecret("test-secret")
        val a = UmamiCrypto.nameBasedUuid("website", "ip", "ua", "salt", secret = secret)
        val b = UmamiCrypto.nameBasedUuid("website", "ip", "ua", "salt", secret = secret)
        assertEquals(a, b)
        assertEquals(5, a.version())
    }

    @Test
    fun `sessionSalt rolls on month boundary in UTC`() {
        val endOfApr = Instant.parse("2026-04-30T23:59:59Z")
        val startOfMay = Instant.parse("2026-05-01T00:00:00Z")
        val midApr = Instant.parse("2026-04-15T12:00:00Z")
        assertEquals(UmamiCrypto.sessionSalt(endOfApr), UmamiCrypto.sessionSalt(midApr))
        assertNotEquals(UmamiCrypto.sessionSalt(endOfApr), UmamiCrypto.sessionSalt(startOfMay))
    }

    @Test
    fun `visitSalt rolls on hour boundary in UTC`() {
        val a = Instant.parse("2026-05-01T12:00:00Z")
        val b = Instant.parse("2026-05-01T12:59:59Z")
        val c = Instant.parse("2026-05-01T13:00:00Z")
        assertEquals(UmamiCrypto.visitSalt(a), UmamiCrypto.visitSalt(b))
        assertNotEquals(UmamiCrypto.visitSalt(a), UmamiCrypto.visitSalt(c))
    }
}
