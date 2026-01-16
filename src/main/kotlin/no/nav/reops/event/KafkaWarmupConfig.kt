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
        try {
            val producer = kafkaTemplate.producerFactory.createProducer()
            producer.use { p -> p.partitionsFor(topic) }
            LOG.info("Kafka producer warmup ok for topic={}", topic)
        } catch (ex: Exception) {
            LOG.warn("Kafka producer warmup failed for topic={} msg={}", topic, ex.message)
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(KafkaWarmupConfig::class.java)
    }
}