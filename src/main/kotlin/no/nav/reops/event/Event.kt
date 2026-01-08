package no.nav.reops.event

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class Event(
    @field:NotBlank
    val type: String,
    val payload: Payload
) {
    data class Payload(
        @field:Size(max = 255)
        @field:NotBlank
        val website: String,

        @field:Size(max = 255)
        @field:NotBlank
        val hostname: String,

        @field:Size(max = 255)
        @field:NotBlank
        val screen: String,

        @field:Size(max = 255)
        @field:NotBlank
        val language: String,

        @field:Size(max = 255)
        @field:NotBlank
        val title: String,

        @field:Size(max = 255)
        @field:NotBlank
        val url: String,

        @field:Size(max = 255)
        @field:NotBlank
        val referrer: String
    )
}
