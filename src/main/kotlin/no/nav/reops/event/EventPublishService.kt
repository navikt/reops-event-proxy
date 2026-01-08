package no.nav.reops.event

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture

const val USER_AGENT = "User-Agent"

@Service
class EventPublishService(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val logger = LoggerFactory.getLogger(EventPublishService::class.java)

    fun publishEventAsync(event: Event, userAgent: String): CompletableFuture<SendResult<String, Event>> {
        val key = UUID.randomUUID().toString()
        val record = ProducerRecord(topic, key, event).apply {
            headers().add(USER_AGENT, userAgent.toByteArray(StandardCharsets.UTF_8))
        }

        val future: CompletableFuture<SendResult<String, Event>> = kafkaTemplate.send(record)

        future.whenComplete { result, ex ->
            if (ex == null) {
                logger.info(
                    "Kafka publish ok topic={} key={} partition={} offset={}",
                    topic, key, result?.recordMetadata?.partition(), result?.recordMetadata?.offset()
                )
            } else {
                logger.warn("Kafka publish failed topic={} key={} msg={}", topic, key, ex.message)
            }
        }

        return future
    }
}