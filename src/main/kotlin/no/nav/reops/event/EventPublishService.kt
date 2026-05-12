package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom

const val USER_AGENT = "user-agent"
const val OPT_OUT_FILTERS = "x-opt-out-filters"
const val FORWARDED_FOR = "x-forwarded-for"
const val SCRIPT_VERSION = "x-script-version"

@Service
class EventPublishService(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    meterRegistry: MeterRegistry,
    @Value("\${spring.kafka.topic}") private val topic: String
) {
    private val kafkaCreated: Counter = meterRegistry.counter("kafka_events_total", "result", "created")
    private val kafkaFailure: Counter = meterRegistry.counter("kafka_events_total", "result", "failure")

    private val objectMapper = jacksonObjectMapper()

    fun publishEventAsync(
        event: Event, userAgent: String, optOutFilters: List<OptOutFilter>?, forwardedFor: String?
    ): CompletableFuture<SendResult<String, Event>> {
        val key = ThreadLocalRandom.current().let { UUID(it.nextLong(), it.nextLong()) }.toString()
        val record = ProducerRecord(topic, key, event).apply {
            headers().add(USER_AGENT, userAgent.toByteArray(UTF_8))
            forwardedFor?.takeIf { it.isNotBlank() }?.let { headers().add(FORWARDED_FOR, it.toByteArray(UTF_8)) }
            optOutFilters?.takeIf { it.isNotEmpty() }?.let {
                headers().add(OPT_OUT_FILTERS, objectMapper.writeValueAsBytes(it))
            }
        }

        return runCatching {
            kafkaTemplate.send(record)
        }.getOrElse { ex ->
            CompletableFuture.failedFuture(ex)
        }.whenComplete { _, ex ->
            if (ex == null) {
                kafkaCreated.increment()
            } else {
                kafkaFailure.increment()
                LOG.warn(
                    "Kafka publish failed topic={} key={} website={} ex={} msg={}",
                    topic,
                    key,
                    event.payload.website,
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