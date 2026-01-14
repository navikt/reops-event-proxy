package no.nav.reops.truncation

import no.nav.reops.event.Event

private fun String.requireNotBlank(fieldName: String): String =
    also { require(it.isNotBlank()) { "$fieldName must not be blank" } }

private fun TruncationValidate.truncateAny(fieldPath: String, value: Any?): Any? =
    when (value) {
        null -> null
        is String -> truncateMarked(fieldPath, value)
        is Map<*, *> -> {
            value.entries.associate { (k, v) ->
                val key = k?.toString()
                key to truncateAny("$fieldPath.$key", v)
            }
        }
        is Iterable<*> -> value.mapIndexed { idx, el -> truncateAny("$fieldPath[$idx]", el) }
        is Array<*> -> value.mapIndexed { idx, el -> truncateAny("$fieldPath[$idx]", el) }.toTypedArray()
        else -> value
    }

fun Event.sanitizeForKafkaWithReport(): SanitizedEvent {
    require(type.isNotBlank()) { "type must not be blank" }
    val trunkCollector = TruncationValidate(MAX_LENGTH)

    val sanitized = copy(
        type = trunkCollector.truncateMarked("type", type),
        payload = Event.Payload(
            website = trunkCollector.truncateMarked("payload.website", payload.website.requireNotBlank("payload.website")),
            hostname = trunkCollector.truncateMarked("payload.hostname", payload.hostname.requireNotBlank("payload.hostname")),
            screen = trunkCollector.truncateMarked("payload.screen", payload.screen.requireNotBlank("payload.screen")),
            language = trunkCollector.truncateMarked("payload.language", payload.language.requireNotBlank("payload.language")),
            title = trunkCollector.truncateMarked("payload.title", payload.title.requireNotBlank("payload.title")),
            url = trunkCollector.truncateMarked("payload.url", payload.url.requireNotBlank("payload.url")),
            referrer = trunkCollector.truncateMarked("payload.referrer", payload.referrer.requireNotBlank("payload.referrer")),
            data = trunkCollector.truncateAny("payload.data", payload.data) as Map<String, Any?>?,
        )
    )

    return SanitizedEvent(event = sanitized, truncationReport = trunkCollector.reportOrNull())
}