package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private HabitStreakRepository habitStreakRepository;
    @Mock
    private StreakCalculatorService streakCalculatorService;

    private UpdateHabitUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new UpdateHabitUseCase(habitRepository, habitLogRepository,
                habitStreakRepository, streakCalculatorService, CLOCK);
    }

    private Habit existingHabit() {
        return new Habit(HABIT_ID, USER_ID, "Old Title", "Old desc", Frequency.DAILY,
                null, null, true, Instant.now(), Instant.now(), null);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should update habit title")
        void shouldUpdateTitle() {
            Habit habit = existingHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitRepository.update(any(Habit.class))).thenAnswer(i -> i.getArgument(0));

            UpdateHabitUseCase.UpdateCommand cmd = new UpdateHabitUseCase.UpdateCommand(
                    "New Title", null, false, null, null, false, null, false, null);

            Habit result = useCase.execute(HABIT_ID, USER_ID, cmd);
            assertEquals("New Title", result.getTitle());
        }

        @Test
        @DisplayName("Should clear description when null is provided with flag")
        void shouldClearDescription() {
            Habit habit = existingHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitRepository.update(any(Habit.class))).thenAnswer(i -> i.getArgument(0));

            UpdateHabitUseCase.UpdateCommand cmd = new UpdateHabitUseCase.UpdateCommand(
                    null, null, true, null, null, false, null, false, null);

            Habit result = useCase.execute(HABIT_ID, USER_ID, cmd);
            assertNull(result.getDescription());
        }

        @Test
        @DisplayName("Should recalculate streak when frequency changes")
        void shouldRecalculateStreakOnFrequencyChange() {
            Habit habit = existingHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitRepository.update(any(Habit.class))).thenAnswer(i -> i.getArgument(0));
            when(habitLogRepository.findLogDatesDesc(HABIT_ID, USER_ID)).thenReturn(Collections.emptyList());
            when(streakCalculatorService.calculate(any(), any(), any(), any()))
                    .thenReturn(new HabitStreak(HABIT_ID, 0, 0, null));
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.of(new HabitStreak(HABIT_ID, 5, 10, null)));

            DayOfWeekSet days = new DayOfWeekSet(Set.of(DayOfWeek.MONDAY));
            UpdateHabitUseCase.UpdateCommand cmd = new UpdateHabitUseCase.UpdateCommand(
                    null, null, false, Frequency.CUSTOM, days, true, null, false, null);

            useCase.execute(HABIT_ID, USER_ID, cmd);

            verify(habitStreakRepository).update(any(HabitStreak.class));
        }

        @Test
        @DisplayName("Should throw when habit not found")
        void shouldThrowWhenNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            UpdateHabitUseCase.UpdateCommand cmd = new UpdateHabitUseCase.UpdateCommand(
                    "title", null, false, null, null, false, null, false, null);

            assertThrows(HabitNotFoundException.class, () -> useCase.execute(HABIT_ID, USER_ID, cmd));
        }

        @Test
        @DisplayName("Should throw when CUSTOM frequency without target days")
        void shouldThrowWhenCustomWithoutDays() {
            Habit habit = existingHabit();
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            UpdateHabitUseCase.UpdateCommand cmd = new UpdateHabitUseCase.UpdateCommand(
                    null, null, false, Frequency.CUSTOM, null, true, null, false, null);

            assertThrows(IllegalArgumentException.class, () -> useCase.execute(HABIT_ID, USER_ID, cmd));
        }
    }
}
