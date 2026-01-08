package no.nav.reops

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndeTilEndeTest {

    @Test
    fun `skal kunne publiserer hendelse på kafka`() {

    }
}