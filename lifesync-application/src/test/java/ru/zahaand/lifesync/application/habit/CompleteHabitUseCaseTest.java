package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLog;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.DuplicateHabitLogException;
import ru.zahaand.lifesync.domain.habit.exception.HabitInactiveException;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private HabitStreakRepository habitStreakRepository;
    @Mock
    private StreakCalculatorService streakCalculatorService;

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
                habitStreakRepository, streakCalculatorService, CLOCK);
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
        @DisplayName("Should complete habit and recalculate streak")
        void shouldCompleteHabitSuccessfully() {
            Habit habit = activeHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID, LOG_DATE, USER_ID))
                    .thenReturn(Optional.empty());
            when(habitLogRepository.save(any(HabitLog.class))).thenAnswer(i -> i.getArgument(0));
            when(habitLogRepository.findLogDatesDesc(HABIT_ID, USER_ID)).thenReturn(Collections.emptyList());
            when(streakCalculatorService.calculate(any(), any(), any(), any()))
                    .thenReturn(new HabitStreak(HABIT_ID, 1, 1, LOG_DATE));
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.of(new HabitStreak(HABIT_ID, 0, 0, null)));

            HabitLog result = useCase.execute(HABIT_ID, USER_ID, LOG_DATE, "Done");

            assertNotNull(result);
            assertEquals(LOG_DATE, result.getLogDate());
            assertEquals("Done", result.getNote());

            ArgumentCaptor<HabitLog> captor = ArgumentCaptor.forClass(HabitLog.class);
            verify(habitLogRepository).save(captor.capture());
            assertEquals(HABIT_ID, captor.getValue().getHabitId());

            verify(habitStreakRepository).update(any(HabitStreak.class));
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
                    new ru.zahaand.lifesync.domain.habit.HabitLogId(UUID.randomUUID()),
                    HABIT_ID, USER_ID, LOG_DATE, null, Instant.now(), Instant.now(), null);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID, LOG_DATE, USER_ID))
                    .thenReturn(Optional.of(existingLog));

            assertThrows(DuplicateHabitLogException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, LOG_DATE, null));
        }
    }
}
