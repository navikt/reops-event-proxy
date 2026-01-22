package no.nav.reops.truncation

import no.nav.reops.event.Event
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

private fun String.requireNotBlank(fieldName: String): String =
    also { require(it.isNotBlank()) { "$fieldName must not be blank" } }

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
    val trunkCollector = TruncationValidate()

    val normalized = copy(
        type = type.trim(),
        payload = payload.copy(
            website = payload.website,
            hostname = payload.hostname.nullIfBlank(),
            screen = payload.screen.nullIfBlank(),
            language = payload.language.nullIfBlank(),
            title = payload.title.nullIfBlank(),
            url = payload.url.nullIfBlank(),
            referrer = payload.referrer.nullIfBlank(),
            data = normalizeDataToObject(payload.data)
        )
    )

    normalized.type.requireNotBlank("type")

    val sanitized = normalized.copy(
        type = trunkCollector.truncateMarked("type", normalized.type),
        payload = Event.Payload(
            website = normalized.payload.website,
            hostname = normalized.payload.hostname?.let {
                trunkCollector.truncateMarked("payload.hostname", it)
            },
            screen = normalized.payload.screen?.let {
                trunkCollector.truncateMarked("payload.screen", it)
            },
            language = normalized.payload.language?.let {
                trunkCollector.truncateMarked("payload.language", it)
            },
            title = normalized.payload.title?.let {
                trunkCollector.truncateMarked("payload.title", it)
            },
            url = normalized.payload.url?.let {
                trunkCollector.truncateMarked("payload.url", it)
            },
            referrer = normalized.payload.referrer?.let {
                trunkCollector.truncateMarked("payload.referrer", it)
            },
            data = trunkCollector.truncateJsonNode("payload.data", normalized.payload.data)
        )
    )

    return SanitizedEvent(
        event = sanitized,
        truncationReport = trunkCollector.reportOrNull()
    )
}