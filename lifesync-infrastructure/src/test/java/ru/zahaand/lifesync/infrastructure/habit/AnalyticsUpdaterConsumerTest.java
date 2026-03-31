package ru.zahaand.lifesync.infrastructure.habit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsUpdaterConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private AnalyticsUpdaterConsumer consumer;

    private static final UUID HABIT_UUID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EVENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new AnalyticsUpdaterConsumer(processedEventRepository);
    }

    private HabitCompletedEvent event() {
        return new HabitCompletedEvent(EVENT_ID, HABIT_UUID, USER_ID,
                LocalDate.of(2026, 3, 30), UUID.randomUUID(), Instant.now());
    }

    private ConsumerRecord<String, HabitCompletedEvent> record(HabitCompletedEvent event) {
        return new ConsumerRecord<>("habit.log.completed", 0, 0L, HABIT_UUID.toString(), event);
    }

    @Nested
    class Consume {

        @Test
        @DisplayName("Should process event and mark as processed")
        void shouldProcessEventAndMarkAsProcessed() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-analytics-updater"))
                    .thenReturn(false);

            consumer.consume(record(evt));

            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-analytics-updater");
        }

        @Test
        @DisplayName("Should skip duplicate event")
        void shouldSkipDuplicateEvent() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-analytics-updater"))
                    .thenReturn(true);

            consumer.consume(record(evt));

            verify(processedEventRepository, never()).save(any(), any(), any());
        }
    }
}
