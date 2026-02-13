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
import java.util.concurrent.ConcurrentHashMap

const val USER_AGENT = "user-agent"
const val EXCLUDE_FILTERS = "x-exclude-filters"
const val FORWARDED_FOR = "x-forwarded-for"

@Service
class EventPublishService(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    private val meterRegistry: MeterRegistry,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val kafkaCreatedCounters = ConcurrentHashMap<String, Counter>()
    private val kafkaFailureCounters = ConcurrentHashMap<String, Counter>()

    private fun kafkaCreatedCounter(websiteId: String): Counter = kafkaCreatedCounters.computeIfAbsent(websiteId) {
        meterRegistry.counter("kafka_events_total", "result", "created", "websiteId", websiteId)
    }

    private fun kafkaFailureCounter(websiteId: String): Counter = kafkaFailureCounters.computeIfAbsent(websiteId) {
        meterRegistry.counter("kafka_events_total", "result", "failure", "websiteId", websiteId)
    }

    fun publishEventAsync(
        event: Event, userAgent: String, excludeFilters: String?, forwardedFor: String?
    ): CompletableFuture<SendResult<String, Event>> {
        val key = UUID.randomUUID().toString()
        val record = ProducerRecord(topic, key, event).apply {
            headers().add(USER_AGENT, userAgent.toByteArray(UTF_8))
            forwardedFor?.takeIf { it.isNotBlank() }?.let { headers().add(FORWARDED_FOR, it.toByteArray(UTF_8)) }
            excludeFilters?.takeIf { it.isNotBlank() }?.let { headers().add(EXCLUDE_FILTERS, it.toByteArray(UTF_8)) }
        }

        val websiteId = event.payload.website.toString()

        return runCatching {
            kafkaTemplate.send(record)
        }.getOrElse { ex ->
            CompletableFuture.failedFuture(ex)
        }.whenComplete { _, ex ->
            if (ex == null) {
                kafkaCreatedCounter(websiteId).increment()
            } else {
                kafkaFailureCounter(websiteId).increment()
                LOG.warn(
                    "Kafka publish failed topic={} key={} websiteId={} ex={} msg={}",
                    topic,
                    key,
                    websiteId,
                    ex.javaClass.simpleName,
                    ex.message?.take(200)
                )
            }
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(EventPublishService::class.java)
    }
}