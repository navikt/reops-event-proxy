package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class EventPublishServiceTest {

    @Test
    fun `publishEventAsync sends record and returns future`() {
        val kafkaTemplate = mock<KafkaTemplate<String, Event>>()
        val topic = "test-topic"
        val meterRegistry = mock<MeterRegistry>()
        val counter = mock<Counter>()
        whenever(meterRegistry.counter("kafka_events_created_total", "topic", topic)).thenReturn(counter)
        val service = EventPublishService(kafkaTemplate, meterRegistry, topic)

        val event = mock<Event>()
        val userAgent = "KakeAgent/1.0"
        val clientRegion = "Vestland"

        val sendFuture: CompletableFuture<SendResult<String, Event>> = CompletableFuture()
        val recordCaptor = argumentCaptor<ProducerRecord<String, Event>>()
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>())).thenReturn(sendFuture)

        val returnedFuture = service.publishEventAsync(event, userAgent, "filter1,filter2", clientRegion)
        verify(kafkaTemplate, times(1)).send(recordCaptor.capture())

        val record = recordCaptor.firstValue
        assertEquals(topic, record.topic())
        assertNotNull(record.key())
        assertEquals(event, record.value())

        val header = record.headers().lastHeader(USER_AGENT)
        assertNotNull(header)
        assertEquals(userAgent, header!!.value().toString(StandardCharsets.UTF_8))

        val regionHeader = record.headers().lastHeader(X_CLIENT_REGION)
        assertNotNull(regionHeader)
        assertEquals(clientRegion, regionHeader!!.value().toString(StandardCharsets.UTF_8))

        sendFuture.complete(mock())
        assertEquals(sendFuture, returnedFuture)
    }

    @Test
    fun `publishEventAsync still returns future when send completes exceptionally`() {
        val kafkaTemplate = mock<KafkaTemplate<String, Event>>()
        val topic = "test-topic"
        val meterRegistry = mock<MeterRegistry>()
        val counter = mock<Counter>()
        whenever(meterRegistry.counter("kafka_events_created_total", "topic", topic)).thenReturn(counter)
        val service = EventPublishService(kafkaTemplate, meterRegistry, topic)

        val event = mock<Event>()
        val userAgent = "JUnit/5"
        val clientRegion = "Nordland"
        val sendFuture: CompletableFuture<SendResult<String, Event>> = CompletableFuture()
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>())).thenReturn(sendFuture)

        val returnedFuture = service.publishEventAsync(event, userAgent, "filter1,filter2", clientRegion)
        sendFuture.completeExceptionally(IllegalStateException("boom"))
        assertEquals(sendFuture, returnedFuture)
    }
}