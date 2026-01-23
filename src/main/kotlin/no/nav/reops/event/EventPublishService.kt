package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.CompletableFuture

const val USER_AGENT = "user-agent"
const val EXCLUDE_FILTERS = "x-exclude-filters"
const val X_CLIENT_REGION = "x-client-region"
const val X_CLIENT_CITY = "x-client-city"

@Service
class EventPublishService(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    meterRegistry: MeterRegistry,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val kafkaEventCounter: Counter = meterRegistry.counter("kafka_events_created_total", "topic", topic)

    fun publishEventAsync(
        event: Event, userAgent: String, excludeFilters: String?, clientRegion: String?, clientCity: String?
    ): CompletableFuture<SendResult<String, Event>> {
        kafkaEventCounter.increment()

        val key = UUID.randomUUID().toString()
        val record = ProducerRecord(topic, key, event).apply {
            headers().add(USER_AGENT, userAgent.toByteArray(UTF_8))
            clientRegion?.takeIf { it.isNotBlank() }?.let { headers().add(X_CLIENT_REGION, it.toByteArray(UTF_8)) }
            clientCity?.takeIf { it.isNotBlank() }?.let { headers().add(X_CLIENT_CITY, it.toByteArray(UTF_8)) }
            excludeFilters?.takeIf { it.isNotBlank() }?.let { headers().add(EXCLUDE_FILTERS, it.toByteArray(UTF_8)) }
        }

        return kafkaTemplate.send(record).also { future ->
            future.whenComplete { _, ex ->
                if (ex == null) {
                    LOG.info("Kafka publish ok topic={} key={}", topic, key)
                } else {
                    LOG.warn("Kafka publish failed topic={} key={} msg={}", topic, key, ex.message)
                }
            }
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventPublishService::class.java)
    }
}
