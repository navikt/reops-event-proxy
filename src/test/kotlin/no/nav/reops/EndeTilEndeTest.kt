package no.nav.reops

import no.nav.reops.event.Event
import no.nav.reops.event.USER_AGENT
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import kotlin.uuid.Uuid

@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = ["test-topic"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndeTilEndeTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    private fun webTestClient(): WebTestClient =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()

    @Test
    fun `skal kunne publiserer hendelse på kafka`() {
        val topic = "test-topic"

        val consumerProps = KafkaTestUtils.consumerProps(embeddedKafka, "e2e-consumer", true).toMutableMap()
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonJsonDeserializer::class.java
        consumerProps[JacksonJsonDeserializer.TRUSTED_PACKAGES] = "*"
        consumerProps[JacksonJsonDeserializer.VALUE_DEFAULT_TYPE] = Event::class.java.name

        val consumer = KafkaConsumer<String, Event>(consumerProps)
        consumer.use { c ->
            embeddedKafka.consumeFromAnEmbeddedTopic(c, topic)

            val event = Event(
                type = "pageview",
                payload = Event.Payload(
                    website = UUID.randomUUID().toString(),
                    hostname = "localhost",
                    screen = "1920x1080",
                    language = "en",
                    title = "Home",
                    url = "https://example.test/",
                    referrer = "https://referrer.test/"
                )
            )

            val userAgent = "JUnit/5"

            webTestClient().post()
                .uri("/api/send")
                .contentType(MediaType.APPLICATION_JSON)
                .header(USER_AGENT, userAgent)
                .bodyValue(event)
                .exchange()
                .expectStatus().isCreated

            val record: ConsumerRecord<String, Event> =
                KafkaTestUtils.getSingleRecord(c, topic, Duration.ofSeconds(10))

            assertNotNull(record.key())
            assertEquals(event, record.value())

            val uaHeader = record.headers().lastHeader(USER_AGENT)
            assertNotNull(uaHeader)
            assertEquals(userAgent, uaHeader.value().toString(StandardCharsets.UTF_8))
        }
    }
}