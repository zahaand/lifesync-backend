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
import ru.zahaand.lifesync.domain.habit.exception.DuplicateHabitLogException;
import ru.zahaand.lifesync.domain.habit.exception.HabitInactiveException;
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
class CompleteHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CompleteHabitUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));
    private static final LocalDate LOG_DATE = LocalDate.of(2026, 3, 30);

    @BeforeEach
    void setUp() {
        useCase = new CompleteHabitUseCase(habitRepository, habitLogRepository,
                eventPublisher, CLOCK);
    }

    private Habit activeHabit() {
        return new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                null, null, true, Instant.now(), Instant.now(), null);
    }

    private Habit inactiveHabit() {
        return new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                null, null, false, Instant.now(), Instant.now(), null);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should complete habit and publish HabitCompletedEvent")
        void shouldCompleteHabitAndPublishEvent() {
            Habit habit = activeHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID, LOG_DATE, USER_ID))
                    .thenReturn(Optional.empty());
            when(habitLogRepository.save(any(HabitLog.class))).thenAnswer(i -> i.getArgument(0));

            HabitLog result = useCase.execute(HABIT_ID, USER_ID, LOG_DATE, "Done");

            assertNotNull(result);
            assertEquals(LOG_DATE, result.getLogDate());
            assertEquals("Done", result.getNote());

            ArgumentCaptor<HabitLog> logCaptor = ArgumentCaptor.forClass(HabitLog.class);
            verify(habitLogRepository).save(logCaptor.capture());
            assertEquals(HABIT_ID, logCaptor.getValue().getHabitId());

            ArgumentCaptor<HabitCompletedEvent> eventCaptor = ArgumentCaptor.forClass(HabitCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            HabitCompletedEvent event = eventCaptor.getValue();
            assertEquals(HABIT_ID.value(), event.habitId());
            assertEquals(USER_ID, event.userId());
            assertEquals(LOG_DATE, event.logDate());
            assertNotNull(event.completionId());
            assertNotNull(event.eventId());
            assertNotNull(event.occurredAt());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when habit not found")
        void shouldThrowWhenHabitNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, LOG_DATE, null));
        }

        @Test
        @DisplayName("Should throw HabitInactiveException when habit is inactive")
        void shouldThrowWhenHabitInactive() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.of(inactiveHabit()));

            assertThrows(HabitInactiveException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, LOG_DATE, null));
        }

        @Test
        @DisplayName("Should throw DuplicateHabitLogException when already completed on date")
        void shouldThrowWhenDuplicateCompletion() {
            Habit habit = activeHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            HabitLog existingLog = new HabitLog(
                    new HabitLogId(UUID.randomUUID()),
                    HABIT_ID, USER_ID, LOG_DATE, null, Instant.now(), Instant.now(), null);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID, LOG_DATE, USER_ID))
                    .thenReturn(Optional.of(existingLog));

            assertThrows(DuplicateHabitLogException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, LOG_DATE, null));
        }

        @Test
        @DisplayName("Should propagate exception when publishEvent throws")
        void shouldPropagateWhenPublishEventThrows() {
            Habit habit = activeHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID, LOG_DATE, USER_ID))
                    .thenReturn(Optional.empty());
            when(habitLogRepository.save(any(HabitLog.class))).thenAnswer(i -> i.getArgument(0));
            doThrow(new RuntimeException("Event bus failure"))
                    .when(eventPublisher).publishEvent(any(HabitCompletedEvent.class));

            assertThrows(RuntimeException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, LOG_DATE, "Done"));
        }
    }
}
