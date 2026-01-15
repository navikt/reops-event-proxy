package no.nav.reops.truncation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

internal const val MAX_LENGTH = 500
private const val TRUNC_SUFFIX = "TRUNCATED"

internal class TruncationValidate(private val limit: Int = MAX_LENGTH) {
    private val violations = mutableListOf<TruncationViolation>()

    fun truncateMarked(field: String, value: String): String {
        if (value.length <= limit) return value
        violations += TruncationViolation(field = field, length = value.length)
        val keep = (limit - TRUNC_SUFFIX.length).coerceAtLeast(0)
        return value.take(keep) + TRUNC_SUFFIX
    }

    fun truncateJsonNode(field: String, node: JsonNode?): JsonNode? {
        if (node == null) return null

        return when {
            node.isString -> {
                JsonNodeFactory.instance.textNode(truncateMarked(field, node.asString()))
            }

            node.isObject -> {
                val src = node as ObjectNode
                val dst = JsonNodeFactory.instance.objectNode()

                for ((name, child) in src.properties()) {
                    dst.set(name, truncateJsonNode("$field.$name", child))
                }

                dst
            }

            node.isArray -> {
                val src = node as ArrayNode
                val dst = JsonNodeFactory.instance.arrayNode()

                for ((idx, child) in src.withIndex()) {
                    dst.add(truncateJsonNode("$field[$idx]", child))
                }

                dst
            }

            else -> node
        }
    }

    fun reportOrNull(): TruncationReport? =
        if (violations.isEmpty()) null else TruncationReport(limit = limit, violations = violations.toList())
}