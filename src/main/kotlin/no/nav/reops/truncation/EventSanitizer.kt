package no.nav.reops.truncation

import no.nav.reops.event.Event
import no.nav.reops.exception.InvalidEventException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

private fun String.requireNotBlank(fieldName: String): String =
    also {
        if (it.isBlank()) throw InvalidEventException("$fieldName must not be blank")
    }

private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun normalizeDataToObject(node: JsonNode?): JsonNode? {
    if (node == null) return null
    if (node.isObject || node.isArray) return node

    val f = JsonNodeFactory.instance
    val obj: ObjectNode = f.objectNode()

    when {
        node.isString -> obj.set("value", f.stringNode(node.asString()))
        node.isNumber -> obj.set("value", node)
        node.isBoolean -> obj.set("value", f.booleanNode(node.asBoolean()))
        else -> obj.set("value", f.stringNode(node.toString()))
    }

    return obj
}

fun Event.sanitizeForKafkaWithReport(): SanitizedEvent {
    val tc = TruncationValidate()

    val trimmedType = type.trim()
    trimmedType.requireNotBlank("type")

    val sanitized = Event(
        type = tc.truncateMarked("type", trimmedType), payload = Event.Payload(
            website = payload.website,
            id = payload.id.nullIfBlank()?.let { tc.truncateMarked("payload.id", it) },
            hostname = payload.hostname.nullIfBlank()?.let { tc.truncateMarked("payload.hostname", it) },
            screen = payload.screen.nullIfBlank()?.let { tc.truncateMarked("payload.screen", it) },
            language = payload.language.nullIfBlank()?.let { tc.truncateMarked("payload.language", it) },
            title = payload.title.nullIfBlank()?.let { tc.truncateMarked("payload.title", it) },
            url = payload.url.nullIfBlank()?.let { tc.truncateMarked("payload.url", it) },
            referrer = payload.referrer.nullIfBlank()?.let { tc.truncateMarked("payload.referrer", it) },
            name = payload.name.nullIfBlank()?.let { tc.truncateMarked("payload.name", it) },
            data = tc.truncateJsonNode("payload.data", normalizeDataToObject(payload.data))
        )
    )

    return SanitizedEvent(
        event = sanitized, truncationReport = tc.reportOrNull()
    )
}