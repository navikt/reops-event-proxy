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
const val FORWARDED_FOR = "x-forwarded-for"

@Service
class EventPublishService(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    meterRegistry: MeterRegistry,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val created: Counter = meterRegistry.counter("kafka_events_created_total", "topic", topic)
    private val ok: Counter = meterRegistry.counter("kafka_events_publish_ok_total", "topic", topic)
    private val fail: Counter = meterRegistry.counter("kafka_events_publish_fail_total", "topic", topic)

    fun publishEventAsync(
        event: Event,
        userAgent: String,
        excludeFilters: String?,
        forwardedFor: String?
    ): CompletableFuture<SendResult<String, Event>> {
        created.increment()

        val key = UUID.randomUUID().toString()
        val record = ProducerRecord(topic, key, event).apply {
            headers().add(USER_AGENT, userAgent.toByteArray(UTF_8))
            forwardedFor?.takeIf { it.isNotBlank() }?.let { headers().add(FORWARDED_FOR, it.toByteArray(UTF_8)) }
            excludeFilters?.takeIf { it.isNotBlank() }?.let { headers().add(EXCLUDE_FILTERS, it.toByteArray(UTF_8)) }
        }

        return kafkaTemplate.send(record).whenComplete { _, ex ->
            if (ex == null) {
                ok.increment()
            } else {
                fail.increment()
                LOG.warn("Kafka publish failed topic={} key={} msg={}", topic, key, ex.message)
            }
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventPublishService::class.java)
    }
}