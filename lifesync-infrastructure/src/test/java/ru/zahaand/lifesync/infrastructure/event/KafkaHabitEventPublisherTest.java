package ru.zahaand.lifesync.infrastructure.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaHabitEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaHabitEventPublisher publisher;

    private static final UUID HABIT_UUID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        publisher = new KafkaHabitEventPublisher(kafkaTemplate);
    }

    private HabitCompletedEvent event() {
        return new HabitCompletedEvent(UUID.randomUUID().toString(), HABIT_UUID, USER_ID,
                LocalDate.of(2026, 3, 30), UUID.randomUUID(), Instant.now());
    }

    @Nested
    class HandleHabitCompletedEvent {

        @Test
        @DisplayName("Should send to correct topic with habitId as partition key")
        void shouldSendToCorrectTopicWithPartitionKey() {
            HabitCompletedEvent evt = event();
            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("habit.log.completed", 0), 0, 0, 0, 0, 0);
            future.complete(new SendResult<>(
                    new ProducerRecord<>("habit.log.completed", HABIT_UUID.toString(), evt), metadata));

            when(kafkaTemplate.send(eq("habit.log.completed"), eq(HABIT_UUID.toString()), eq(evt)))
                    .thenReturn(future);

            publisher.handleHabitCompletedEvent(evt);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eq(evt));
            assertEquals("habit.log.completed", topicCaptor.getValue());
            assertEquals(HABIT_UUID.toString(), keyCaptor.getValue());
        }

        @Test
        @DisplayName("Should not propagate exception when Kafka fails")
        void shouldNotPropagateExceptionOnKafkaFailure() {
            HabitCompletedEvent evt = event();
            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

            when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .thenReturn(future);

            publisher.handleHabitCompletedEvent(evt);

            verify(kafkaTemplate).send(eq("habit.log.completed"), eq(HABIT_UUID.toString()), eq(evt));
        }
    }
}
