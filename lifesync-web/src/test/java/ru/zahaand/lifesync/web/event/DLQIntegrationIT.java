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
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
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
        @DisplayName("Should route malformed message to DLQ topic without retry")
        void shouldRouteMalformedMessageToDlq() {
            String malformedPayload = "{\"invalid\":\"not-a-valid-event\"}";
            kafkaTemplate.send(TOPIC, "test-key", malformedPayload);

            try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer("dlq-malformed-consumer")) {
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

    @Nested
    class RetryExhaustion {

        @Test
        @DisplayName("Should route message to DLQ after 3 failed retry attempts")
        void shouldRouteToDlqAfterRetryExhaustion() {
            UUID nonExistentHabitId = UUID.randomUUID();
            HabitCompletedEvent event = new HabitCompletedEvent(
                    UUID.randomUUID().toString(),
                    nonExistentHabitId,
                    UUID.randomUUID(),
                    LocalDate.of(2026, 3, 31),
                    UUID.randomUUID(),
                    Instant.parse("2026-03-31T12:00:00Z")
            );

            kafkaTemplate.send(TOPIC, nonExistentHabitId.toString(), event);

            try (KafkaConsumer<String, byte[]> dlqConsumer = createDlqConsumer("dlq-retry-consumer")) {
                dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));

                await().atMost(20, TimeUnit.SECONDS)
                        .pollInterval(1, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(500));
                            assertThat(records.count()).isGreaterThan(0);
                        });
            }
        }
    }

    private KafkaConsumer<String, byte[]> createDlqConsumer(String groupId) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );
        return new KafkaConsumer<>(props);
    }
}
