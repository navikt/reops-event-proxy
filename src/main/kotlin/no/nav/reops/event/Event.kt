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
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val hostname: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val screen: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val language: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val title: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val url: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val referrer: String? = null,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val data: JsonNode? = null
    )
}