package no.nav.reops.truncation

import no.nav.reops.event.Event
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

private fun String.requireNotBlank(fieldName: String): String =
    also { require(it.isNotBlank()) { "$fieldName must not be blank" } }

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
    require(type.isNotBlank()) { "type must not be blank" }
    val trunkCollector = TruncationValidate()

    val normalized = copy(
        payload = payload.copy(
            data = normalizeDataToObject(payload.data)
        )
    )

    val sanitized = normalized.copy(
        type = trunkCollector.truncateMarked("type", normalized.type),
        payload = Event.Payload(
            website = trunkCollector.truncateMarked(
                "payload.website",
                normalized.payload.website.requireNotBlank("payload.website")
            ),
            hostname = trunkCollector.truncateMarked(
                "payload.hostname",
                normalized.payload.hostname.requireNotBlank("payload.hostname")
            ),
            screen = trunkCollector.truncateMarked(
                "payload.screen",
                normalized.payload.screen.requireNotBlank("payload.screen")
            ),
            language = trunkCollector.truncateMarked(
                "payload.language",
                normalized.payload.language.requireNotBlank("payload.language")
            ),
            title = trunkCollector.truncateMarked(
                "payload.title",
                normalized.payload.title.requireNotBlank("payload.title")
            ),
            url = trunkCollector.truncateMarked(
                "payload.url",
                normalized.payload.url.requireNotBlank("payload.url")
            ),
            referrer = trunkCollector.truncateMarked(
                "payload.referrer",
                normalized.payload.referrer.requireNotBlank("payload.referrer")
            ),
            data = trunkCollector.truncateJsonNode("payload.data", normalized.payload.data)
        )
    )

    return SanitizedEvent(
        event = sanitized,
        truncationReport = trunkCollector.reportOrNull()
    )
}