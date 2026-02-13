package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.CompletableFuture

class EventPublishServiceTest {

    @Test
    fun `publishEventAsync sends record and returns future`() {
        val kafkaTemplate = mock<KafkaTemplate<String, Event>>()
        val topic = "test-topic"
        val meterRegistry = mock<MeterRegistry>()

        val createdCounter = mock<Counter>()
        val failureCounter = mock<Counter>()
        val websiteId = UUID.randomUUID().toString()
        doReturn(createdCounter).whenever(meterRegistry)
            .counter("kafka_events_total", "result", "created", "websiteId", websiteId)
        doReturn(failureCounter).whenever(meterRegistry)
            .counter("kafka_events_total", "result", "failure", "websiteId", websiteId)

        val service = EventPublishService(kafkaTemplate, meterRegistry, topic)

        val event = Event(
            type = "test",
            payload = Event.Payload(website = UUID.fromString(websiteId))
        )
        val userAgent = "KakeAgent/1.0"
        val forwardedFor = "127.0.0.1"
        val excludeFilters = "filter1,filter2"

        val sendFuture: CompletableFuture<SendResult<String, Event>> = CompletableFuture()
        val recordCaptor = argumentCaptor<ProducerRecord<String, Event>>()
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>())).thenReturn(sendFuture)

        val returnedFuture = service.publishEventAsync(
            event = event,
            userAgent = userAgent,
            excludeFilters = excludeFilters,
            forwardedFor = forwardedFor,
        )

        verify(kafkaTemplate, times(1)).send(recordCaptor.capture())

        val record = recordCaptor.firstValue
        assertEquals(topic, record.topic())
        assertNotNull(record.key())
        assertEquals(event, record.value())

        val uaHeader = record.headers().lastHeader(USER_AGENT)
        assertNotNull(uaHeader)
        assertEquals(userAgent, uaHeader!!.value().toString(UTF_8))

        val forwardedForHeader = record.headers().lastHeader(FORWARDED_FOR)
        assertNotNull(forwardedForHeader)
        assertEquals(forwardedFor, forwardedForHeader!!.value().toString(UTF_8))

        val excludeHeader = record.headers().lastHeader(EXCLUDE_FILTERS)
        assertNotNull(excludeHeader)
        assertEquals(excludeFilters, excludeHeader!!.value().toString(UTF_8))

        // Complete the future and verify the returned future completes
        val mockSendResult = mock<SendResult<String, Event>>()
        sendFuture.complete(mockSendResult)

        assertNotNull(returnedFuture.get())

        // Metrics
        verify(createdCounter, times(1)).increment()
        verifyNoInteractions(failureCounter)
    }

    @Test
    fun `publishEventAsync still returns future when send completes exceptionally`() {
        val kafkaTemplate = mock<KafkaTemplate<String, Event>>()
        val topic = "test-topic"
        val meterRegistry = mock<MeterRegistry>()

        val createdCounter = mock<Counter>()
        val failureCounter = mock<Counter>()
        val websiteId = UUID.randomUUID().toString()
        doReturn(createdCounter).whenever(meterRegistry)
            .counter("kafka_events_total", "result", "created", "websiteId", websiteId)
        doReturn(failureCounter).whenever(meterRegistry)
            .counter("kafka_events_total", "result", "failure", "websiteId", websiteId)

        val service = EventPublishService(kafkaTemplate, meterRegistry, topic)

        val event = Event(
            type = "test",
            payload = Event.Payload(website = UUID.fromString(websiteId))
        )
        val userAgent = "JUnit/5"
        val forwardedFor = "127.0.0.1"
        val excludeFilters = "filter1,filter2"

        val sendFuture: CompletableFuture<SendResult<String, Event>> = CompletableFuture()
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>())).thenReturn(sendFuture)

        val returnedFuture = service.publishEventAsync(
            event = event,
            userAgent = userAgent,
            excludeFilters = excludeFilters,
            forwardedFor = forwardedFor,
        )

        sendFuture.completeExceptionally(IllegalStateException("boom"))

        try {
            returnedFuture.get()
            throw AssertionError("Expected future to complete exceptionally")
        } catch (e: Exception) {
            assertNotNull(e)
        }

        verify(failureCounter, times(1)).increment()
        verifyNoInteractions(createdCounter)
    }
}
