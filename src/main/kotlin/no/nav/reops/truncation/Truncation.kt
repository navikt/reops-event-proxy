package no.nav.reops.truncation

import no.nav.reops.event.Event

data class TruncationViolation(
    val field: String, val length: Int
)

data class TruncationReport(
    val limit: Int, val violations: List<TruncationViolation>
)

data class SanitizedEvent(
    val event: Event, val truncationReport: TruncationReport?
)