package no.nav.reops.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
    val type: String,
    val payload: Payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Payload(
        val website: String,
        val hostname: String,
        val screen: String,
        val language: String,
        val title: String,
        val url: String,
        val referrer: String,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val data: JsonNode? = null
    )
}