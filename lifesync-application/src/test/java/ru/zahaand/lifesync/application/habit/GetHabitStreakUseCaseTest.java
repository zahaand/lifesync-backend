package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitStreakUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitStreakRepository habitStreakRepository;

    private GetHabitStreakUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        useCase = new GetHabitStreakUseCase(habitRepository, habitStreakRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return streak when found")
        void shouldReturnStreakWhenFound() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            HabitStreak streak = new HabitStreak(HABIT_ID, 5, 10, LocalDate.of(2026, 3, 30));
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.of(streak));

            HabitStreak result = useCase.execute(HABIT_ID, USER_ID);

            assertEquals(5, result.currentStreak());
            assertEquals(10, result.longestStreak());
        }

        @Test
        @DisplayName("Should return zero streak when no streak record exists")
        void shouldReturnZeroStreakWhenNotFound() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.empty());

            HabitStreak result = useCase.execute(HABIT_ID, USER_ID);

            assertEquals(0, result.currentStreak());
            assertEquals(0, result.longestStreak());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when habit not found")
        void shouldThrowWhenHabitNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class, () -> useCase.execute(HABIT_ID, USER_ID));
        }
    }
}
