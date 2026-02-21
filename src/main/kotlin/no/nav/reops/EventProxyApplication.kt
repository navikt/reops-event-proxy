package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@SpringBootApplication
@ImportRuntimeHints(NativeHintsRegistrar::class)
class EventProxyApplication

fun main(args: Array<String>) {
    runApplication<EventProxyApplication>(*args)
}
