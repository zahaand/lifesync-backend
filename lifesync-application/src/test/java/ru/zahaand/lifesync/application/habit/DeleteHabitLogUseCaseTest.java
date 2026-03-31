package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteHabitLogUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeleteHabitLogUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final HabitLogId LOG_ID = new HabitLogId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new DeleteHabitLogUseCase(habitRepository, habitLogRepository,
                eventPublisher, CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should soft-delete habit log and publish HabitCompletedEvent")
        void shouldDeleteLogAndPublishEvent() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            HabitLog log = new HabitLog(LOG_ID, HABIT_ID, USER_ID, LocalDate.of(2026, 3, 29),
                    null, Instant.now(), Instant.now(), null);

            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByIdAndUserId(LOG_ID, USER_ID)).thenReturn(Optional.of(log));
            when(habitLogRepository.update(any(HabitLog.class))).thenAnswer(i -> i.getArgument(0));

            useCase.execute(HABIT_ID, LOG_ID, USER_ID);

            ArgumentCaptor<HabitLog> logCaptor = ArgumentCaptor.forClass(HabitLog.class);
            verify(habitLogRepository).update(logCaptor.capture());
            assertNotNull(logCaptor.getValue().getDeletedAt());

            ArgumentCaptor<HabitCompletedEvent> eventCaptor = ArgumentCaptor.forClass(HabitCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            HabitCompletedEvent event = eventCaptor.getValue();
            assertEquals(HABIT_ID.value(), event.habitId());
            assertEquals(USER_ID, event.userId());
            assertEquals(LocalDate.of(2026, 3, 29), event.logDate());
            assertEquals(LOG_ID.value(), event.completionId());
            assertNotNull(event.eventId());
            assertNotNull(event.occurredAt());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when habit not found")
        void shouldThrowWhenHabitNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class,
                    () -> useCase.execute(HABIT_ID, LOG_ID, USER_ID));
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when log not found")
        void shouldThrowWhenLogNotFound() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByIdAndUserId(LOG_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class,
                    () -> useCase.execute(HABIT_ID, LOG_ID, USER_ID));
        }

        @Test
        @DisplayName("Should not propagate exception when publishEvent throws")
        void shouldNotPropagateWhenPublishEventThrows() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            HabitLog log = new HabitLog(LOG_ID, HABIT_ID, USER_ID, LocalDate.of(2026, 3, 29),
                    null, Instant.now(), Instant.now(), null);

            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByIdAndUserId(LOG_ID, USER_ID)).thenReturn(Optional.of(log));
            when(habitLogRepository.update(any(HabitLog.class))).thenAnswer(i -> i.getArgument(0));
            doThrow(new RuntimeException("Event bus failure"))
                    .when(eventPublisher).publishEvent(any(HabitCompletedEvent.class));

            assertDoesNotThrow(() -> useCase.execute(HABIT_ID, LOG_ID, USER_ID));
        }
    }
}
