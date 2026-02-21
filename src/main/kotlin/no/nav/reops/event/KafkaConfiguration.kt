package no.nav.reops.event

import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@Configuration
class KafkaProducerConfig(
    private val kafkaProperties: KafkaProperties,
    private val jsonMapper: JsonMapper
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, Event> {
        val configProps = kafkaProperties.buildProducerProperties()
        return DefaultKafkaProducerFactory(
            configProps, StringSerializer(), JacksonJsonSerializer(jsonMapper)
        )
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Event> = KafkaTemplate(producerFactory())
}
