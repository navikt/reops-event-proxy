package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    meterRegistry: MeterRegistry,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val logger = LoggerFactory.getLogger(EventPublishService::class.java)
    private val kafkaEventCounter: Counter = meterRegistry.counter("kafka_events_created_total", "topic", topic)

    fun publishEventAsync(event: Event, userAgent: String): CompletableFuture<SendResult<String, Event>> {
        kafkaEventCounter.increment()
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