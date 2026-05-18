package no.nav.reops.session

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ResolvedSession(
    val sessionId: UUID,
    val visitId: UUID,
    val iat: Long,
    val createdAt: Instant,
    val cacheToken: String
)

@Service
class SessionResolver(
    @Value("\${app.secret:}") rawAppSecret: String
) {
    private val derivedSecret: String = UmamiCrypto.derivedSecret(rawAppSecret)
    private val tokenCodec = UmamiCacheToken(derivedSecret)

    fun resolve(
        websiteId: UUID,
        ip: String?,
        userAgent: String?,
        identity: String?,
        incomingCacheToken: String?,
        now: Instant = Instant.now()
    ): ResolvedSession {
        val nowSeconds = now.epochSecond
        val cache = tokenCodec.parse(incomingCacheToken)
            ?.takeIf { it.websiteId == websiteId }

        val sessionId = cache?.sessionId
            ?: computeSessionId(websiteId, ip, userAgent, identity, now)

        val visitSalt = UmamiCrypto.visitSalt(now)
        val rolledOver = cache != null && nowSeconds - cache.iat > VISIT_IDLE_TIMEOUT_SECONDS
        val visitId: UUID
        val iat: Long
        when {
            cache == null -> {
                visitId = UmamiCrypto.nameBasedUuid(sessionId.toString(), visitSalt, secret = derivedSecret)
                iat = nowSeconds
            }
            rolledOver -> {
                visitId = UmamiCrypto.nameBasedUuid(sessionId.toString(), visitSalt, nowSeconds.toString(), secret = derivedSecret)
                iat = nowSeconds
                LOG.debug("Visit timeout exceeded; minted new visitId for sessionId={}", sessionId)
            }
            else -> {
                visitId = cache.visitId
                iat = cache.iat
            }
        }

        val claims = CacheClaims(
            websiteId = websiteId,
            sessionId = sessionId,
            visitId = visitId,
            iat = iat
        )
        return ResolvedSession(
            sessionId = sessionId,
            visitId = visitId,
            iat = iat,
            createdAt = now,
            cacheToken = tokenCodec.create(claims)
        )
    }

    private fun computeSessionId(
        websiteId: UUID,
        ip: String?,
        userAgent: String?,
        identity: String?,
        now: Instant
    ): UUID {
        val websiteIdStr = websiteId.toString()
        return if (!identity.isNullOrBlank()) {
            UmamiCrypto.nameBasedUuid(websiteIdStr, identity, secret = derivedSecret)
        } else {
            val sessionSalt = UmamiCrypto.sessionSalt(now)
            UmamiCrypto.nameBasedUuid(
                websiteIdStr,
                ip.orEmpty(),
                userAgent.orEmpty(),
                sessionSalt,
                secret = derivedSecret
            )
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(SessionResolver::class.java)
        private const val VISIT_IDLE_TIMEOUT_SECONDS = 1800L
    }
}
