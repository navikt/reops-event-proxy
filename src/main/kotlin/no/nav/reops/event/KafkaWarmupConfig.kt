package no.nav.reops.event

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class KafkaWarmupConfig(
    private val kafkaTemplate: KafkaTemplate<String, Event>, @Value("\${spring.kafka.topic}") private val topic: String
) {

    @Bean
    fun kafkaWarmupRunner(): ApplicationRunner = ApplicationRunner {
        var attempts = 0
        val maxAttempts = 5
        var delayMs = 250L

        while (attempts < maxAttempts) {
            try {
                val producer = kafkaTemplate.producerFactory.createProducer()
                producer.use { p -> p.partitionsFor(topic) }
                LOG.info("Kafka producer warmup ok for topic={} after {} attempt(s)", topic, attempts + 1)
                return@ApplicationRunner
            } catch (ex: Exception) {
                attempts++
                if (attempts >= maxAttempts) {
                    LOG.warn("Kafka producer warmup failed for topic={} after {} attempts. msg={}", topic, maxAttempts, ex.message)
                } else {
                    LOG.debug("Kafka producer warmup attempt {}/{} failed for topic={}, retrying in {}ms", attempts, maxAttempts, topic, delayMs)
                    Thread.sleep(delayMs)
                    delayMs *= 2
                }
            }
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(KafkaWarmupConfig::class.java)
    }
}