package no.nav.reops.event

data class Event(
    val type: String,
    val payload: Payload
) {
    data class Payload(
        val website: String,
        val hostname: String,
        val screen: String,
        val language: String,
        val title: String,
        val url: String,
        val referrer: String,
        val data: Map<String, Any?>? = null,
    )
}