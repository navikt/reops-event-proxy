package no.nav.reops.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionResolverTest {

    private val resolver = SessionResolver("test-secret")
    private val websiteId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `cache miss mints fresh session and visit and signed token`() {
        val now = Instant.parse("2026-05-08T10:00:00Z")
        val r = resolver.resolve(
            websiteId = websiteId,
            ip = "1.2.3.4",
            userAgent = "JUnit/5",
            identity = null,
            incomingCacheToken = null,
            now = now
        )
        assertEquals(now.epochSecond, r.iat)
        assertTrue(r.cacheToken.isNotBlank())
    }

    @Test
    fun `same inputs within same month and hour produce same session and visit on cache miss`() {
        val now = Instant.parse("2026-05-08T10:00:00Z")
        val a = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, now)
        val b = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, now.plusSeconds(60))
        assertEquals(a.sessionId, b.sessionId)
        assertEquals(a.visitId, b.visitId)
    }

    @Test
    fun `valid cache within 30 minutes preserves visitId and iat`() {
        val t0 = Instant.parse("2026-05-08T10:00:00Z")
        val first = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, t0)
        val t1 = t0.plusSeconds(29 * 60)
        val second = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, first.cacheToken, t1)
        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.visitId, second.visitId)
        assertEquals(first.iat, second.iat)
    }

    @Test
    fun `cache older than 30 minutes mints new visitId and resets iat`() {
        val t0 = Instant.parse("2026-05-08T10:00:00Z")
        val first = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, t0)
        val t1 = t0.plusSeconds(31 * 60)
        val second = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, first.cacheToken, t1)
        assertEquals(first.sessionId, second.sessionId)
        assertNotEquals(first.visitId, second.visitId)
        assertEquals(t1.epochSecond, second.iat)
    }

    @Test
    fun `tampered cache token is treated as cache miss`() {
        val t0 = Instant.parse("2026-05-08T10:00:00Z")
        val first = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, t0)
        val tampered = first.cacheToken.dropLast(5) + "AAAAA"
        val r = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, tampered, t0.plusSeconds(60))
        assertEquals(first.sessionId, r.sessionId)
        assertEquals(t0.plusSeconds(60).epochSecond, r.iat)
    }

    @Test
    fun `cache for a different websiteId is rejected`() {
        val t0 = Instant.parse("2026-05-08T10:00:00Z")
        val first = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", null, null, t0)
        val otherWebsite = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")
        val r = resolver.resolve(otherWebsite, "1.2.3.4", "JUnit/5", null, first.cacheToken, t0.plusSeconds(60))
        assertNotEquals(first.sessionId, r.sessionId)
    }

    @Test
    fun `identify path uses websiteId and identity for sessionId`() {
        val now = Instant.parse("2026-05-08T10:00:00Z")
        val a = resolver.resolve(websiteId, "1.2.3.4", "JUnit/5", "user-123", null, now)
        val b = resolver.resolve(websiteId, "9.9.9.9", "OtherUA/1", "user-123", null, now)
        assertEquals(a.sessionId, b.sessionId)
    }

    @Test
    fun `parseToken returns null for blank input`() {
        val codec = UmamiCacheToken("test-secret")
        assertNull(codec.parse(""))
        assertNull(codec.parse(null))
        assertNull(codec.parse("not-a-jwt"))
    }
}
