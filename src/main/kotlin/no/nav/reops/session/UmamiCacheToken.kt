package no.nav.reops.session

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.JWTVerifier
import java.util.UUID

data class CacheClaims(
    val websiteId: UUID,
    val sessionId: UUID,
    val visitId: UUID,
    val iat: Long
)

class UmamiCacheToken(secret: String) {

    private val algorithm: Algorithm = Algorithm.HMAC256(secret.ifEmpty { FALLBACK_SECRET }.toByteArray(Charsets.UTF_8))
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    fun parse(token: String?): CacheClaims? {
        if (token.isNullOrBlank()) return null
        return try {
            val decoded = verifier.verify(token)
            CacheClaims(
                websiteId = UUID.fromString(decoded.getClaim("websiteId").asString()),
                sessionId = UUID.fromString(decoded.getClaim("sessionId").asString()),
                visitId = UUID.fromString(decoded.getClaim("visitId").asString()),
                iat = decoded.getClaim("iat").asLong()
            )
        } catch (_: JWTVerificationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun create(claims: CacheClaims): String =
        JWT.create()
            .withClaim("websiteId", claims.websiteId.toString())
            .withClaim("sessionId", claims.sessionId.toString())
            .withClaim("visitId", claims.visitId.toString())
            .withClaim("iat", claims.iat)
            .sign(algorithm)

    companion object {
        private const val FALLBACK_SECRET = "reops-event-proxy-fallback-secret-do-not-use-in-prod"
    }
}
