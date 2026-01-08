package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventProxyApplication

fun main(args: Array<String>) {
	runApplication<EventProxyApplication>(*args)
}
