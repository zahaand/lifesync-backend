package ru.zahaand.lifesync.infrastructure.habit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.application.habit.StreakCalculatorService;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakCalculatorConsumerTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private HabitStreakRepository habitStreakRepository;
    @Mock
    private StreakCalculatorService streakCalculatorService;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private StreakCalculatorConsumer consumer;

    private static final UUID HABIT_UUID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(HABIT_UUID);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final LocalDate LOG_DATE = LocalDate.of(2026, 3, 30);

    @BeforeEach
    void setUp() {
        consumer = new StreakCalculatorConsumer(habitRepository, habitLogRepository,
                habitStreakRepository, streakCalculatorService, processedEventRepository);
    }

    private HabitCompletedEvent event() {
        return new HabitCompletedEvent(EVENT_ID, HABIT_UUID, USER_ID, LOG_DATE,
                UUID.randomUUID(), Instant.now());
    }

    private ConsumerRecord<String, HabitCompletedEvent> record(HabitCompletedEvent event) {
        return new ConsumerRecord<>("habit.log.completed", 0, 0L, HABIT_UUID.toString(), event);
    }

    private Habit habit() {
        return new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                null, null, true, Instant.now(), Instant.now(), null);
    }

    @Nested
    class Consume {

        @Test
        @DisplayName("Should recalculate streak and save when no existing streak")
        void shouldRecalculateStreakAndSave() {
            HabitCompletedEvent evt = event();
            Habit h = habit();
            HabitStreak streak = new HabitStreak(HABIT_ID, 1, 1, LOG_DATE);

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-streak-calculator"))
                    .thenReturn(false);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(h));
            when(habitLogRepository.findLogDatesDesc(HABIT_ID, USER_ID)).thenReturn(List.of(LOG_DATE));
            when(streakCalculatorService.calculate(eq(HABIT_ID), eq(Frequency.DAILY), any(), eq(List.of(LOG_DATE))))
                    .thenReturn(streak);
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            consumer.consume(record(evt));

            verify(habitStreakRepository).save(streak);
            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-streak-calculator");
        }

        @Test
        @DisplayName("Should update existing streak")
        void shouldUpdateExistingStreak() {
            HabitCompletedEvent evt = event();
            Habit h = habit();
            HabitStreak oldStreak = new HabitStreak(HABIT_ID, 0, 0, null);
            HabitStreak newStreak = new HabitStreak(HABIT_ID, 1, 1, LOG_DATE);

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-streak-calculator"))
                    .thenReturn(false);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(h));
            when(habitLogRepository.findLogDatesDesc(HABIT_ID, USER_ID)).thenReturn(List.of(LOG_DATE));
            when(streakCalculatorService.calculate(eq(HABIT_ID), eq(Frequency.DAILY), any(), eq(List.of(LOG_DATE))))
                    .thenReturn(newStreak);
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(oldStreak));

            consumer.consume(record(evt));

            verify(habitStreakRepository).update(newStreak);
            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-streak-calculator");
        }

        @Test
        @DisplayName("Should skip duplicate event")
        void shouldSkipDuplicateEvent() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-streak-calculator"))
                    .thenReturn(true);

            consumer.consume(record(evt));

            verify(habitRepository, never()).findByIdAndUserId(any(), any());
            verify(streakCalculatorService, never()).calculate(any(), any(), any(), any());
            verify(processedEventRepository, never()).save(any(), any(), any());
        }

        @Test
        @DisplayName("Should throw when habit not found")
        void shouldThrowWhenHabitNotFound() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-streak-calculator"))
                    .thenReturn(false);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class, () -> consumer.consume(record(evt)));
        }

        @Test
        @DisplayName("Should propagate exception when idempotency check fails")
        void shouldPropagateWhenIdempotencyCheckFails() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-streak-calculator"))
                    .thenThrow(new RuntimeException("DB connection lost"));

            assertThrows(RuntimeException.class, () -> consumer.consume(record(evt)));
        }
    }
}
