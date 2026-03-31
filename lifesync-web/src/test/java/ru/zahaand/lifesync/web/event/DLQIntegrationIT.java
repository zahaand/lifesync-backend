package ru.zahaand.lifesync.web.event;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DLQIntegrationIT extends BaseIT {

    private static final String TOPIC = "habit.log.completed";
    private static final String DLQ_TOPIC = "habit.log.completed.dlq";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Nested
    class MalformedMessage {

        @Test
        @DisplayName("Should route malformed message to DLQ topic")
        void shouldRouteMalformedMessageToDlq() {
            String malformedPayload = "{\"invalid\":\"not-a-valid-event\"}";
            kafkaTemplate.send(TOPIC, "test-key", malformedPayload);

            try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer()) {
                dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));

                await().atMost(10, TimeUnit.SECONDS)
                        .pollInterval(500, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(200));
                            assertThat(records.count()).isGreaterThan(0);
                        });
            }
        }
    }

    private KafkaConsumer<String, byte[]> createDlqConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );
        return new KafkaConsumer<>(props);
    }
}
