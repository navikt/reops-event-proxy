package no.nav.reops.session

import com.fasterxml.uuid.Generators
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Bit-for-bit Kotlin port of upstream Umami's session/visit ID crypto helpers, so that
 * IDs minted here match what `umami-software/umami` would produce given the same inputs.
 *
 * Reference: https://github.com/umami-software/umami/blob/a9508e7aaeb5440897c70a803b5933fd69b492e6/src/lib/crypto.ts
 *
 *  - HASH_ALGO    = SHA-512 (note: not SHA-256), hex output
 *  - args joined with the empty string (no separator)
 *  - UUID v5 namespace = DNS namespace (RFC 4122)
 *  - `secret()` returns hash(APP_SECRET); itself a SHA-512 hex string
 *  - sessionSalt = hash(startOfMonth(createdAt).toUTCString())  (default rotation = month)
 *  - visitSalt   = hash(startOfHour(createdAt).toUTCString())
 *
 * `Date.toUTCString()` in JavaScript follows RFC 7231, e.g. `"Mon, 05 May 2025 13:00:00 GMT"`.
 * We mirror that exactly.
 */
object UmamiCrypto {

    /** RFC 4122 DNS namespace UUID, identical to `uuid` npm package's `v5.DNS`. */
    private val DNS_NAMESPACE: UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

    private val v5Generator = Generators.nameBasedGenerator(DNS_NAMESPACE)

    /**
     * RFC 7231 IMF-fixdate, matching `Date.prototype.toUTCString()` in JavaScript.
     * Locale.ENGLISH ensures English month/day names; ZoneOffset.UTC ensures the trailing "GMT".
     */
    private val UTC_STRING: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)

    /**
     * `hash(...args)` — SHA-512 of `args.join('')`, hex-encoded.
     * Mirrors `crypto.createHash('sha512').update(args.join('')).digest('hex')`.
     */
    fun hash(vararg args: String): String {
        val md = MessageDigest.getInstance("SHA-512")
        val joined = args.joinToString(separator = "")
        val bytes = md.digest(joined.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /**
     * `secret()` — SHA-512 hash of APP_SECRET (or fallback). Caller passes the resolved
     * secret string. Upstream falls back to DATABASE_URL; we treat empty string as "no salt"
     * (matching the existing reops-umami behaviour where `app.secret:` defaults to "").
     */
    fun derivedSecret(rawSecret: String): String = hash(rawSecret)

    /**
     * `uuid(...args)` with args present — name-based UUIDv5 over DNS namespace,
     * input = `hash(...args, secret())`.
     */
    fun nameBasedUuid(vararg args: String, secret: String): UUID {
        val combined = arrayOf(*args, secret)
        val name = hash(*combined)
        return v5Generator.generate(name)
    }

    /**
     * `getSalt('month', date)` — hash of the start of the month in UTC, formatted as
     * `Date.prototype.toUTCString()`. Upstream defaults to month rotation.
     */
    fun sessionSalt(now: Instant): String {
        val startOfMonthUtc = ZonedDateTime.ofInstant(now, ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        return hash(startOfMonthUtc.format(UTC_STRING))
    }

    /**
     * `hash(startOfHour(createdAt).toUTCString())` — visit salt; rolls every hour boundary.
     * Note: the rolling-30-minute idle check in [SessionResolver] uses `iat`, not this salt.
     * The salt only affects which UUID the visit gets when no cache is present.
     */
    fun visitSalt(now: Instant): String {
        val startOfHourUtc = ZonedDateTime.ofInstant(now, ZoneOffset.UTC)
            .withMinute(0).withSecond(0).withNano(0)
        return hash(startOfHourUtc.format(UTC_STRING))
    }
}
