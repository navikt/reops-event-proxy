package no.nav.reops.truncation

internal const val MAX_LENGTH = 500
private const val TRUNC_SUFFIX = "TRUNCATED"

internal class TruncationValidate(private val limit: Int) {
    private val violations = mutableListOf<TruncationViolation>()

    fun truncateMarked(field: String, value: String): String {
        if (value.length <= limit) return value
        violations += TruncationViolation(field = field, length = value.length)
        val keep = (limit - TRUNC_SUFFIX.length).coerceAtLeast(0)
        return value.take(keep) + TRUNC_SUFFIX
    }

    fun reportOrNull(): TruncationReport? =
        if (violations.isEmpty()) null else TruncationReport(limit = limit, violations = violations.toList())
}