package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    @Mock
    private HabitStreakRepository habitStreakRepository;

    private GetHabitUseCase useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 3);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new GetHabitUseCase(habitRepository, habitLogRepository, habitStreakRepository, FIXED_CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return enriched habit when found with today's log and streak")
        void shouldReturnEnrichedHabitWithLogAndStreak() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            HabitLogId logId = new HabitLogId(UUID.randomUUID());
            HabitLog todayLog = new HabitLog(logId, HABIT_ID, USER_ID, TODAY, null,
                    Instant.now(), Instant.now(), null);
            when(habitLogRepository.findTodayLogsByHabitIds(List.of(HABIT_ID), TODAY))
                    .thenReturn(Map.of(HABIT_ID, todayLog));

            HabitStreak streak = new HabitStreak(HABIT_ID, 3, 7, TODAY);
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.of(streak));

            EnrichedHabit result = useCase.execute(HABIT_ID, USER_ID);

            assertEquals("Test", result.habit().getTitle());
            assertTrue(result.completedToday());
            assertEquals(logId, result.todayLogId());
            assertEquals(3, result.currentStreak());
        }

        @Test
        @DisplayName("Should return enriched habit with defaults when no log or streak")
        void shouldReturnEnrichedHabitWithDefaults() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
            when(habitLogRepository.findTodayLogsByHabitIds(List.of(HABIT_ID), TODAY))
                    .thenReturn(Map.of());
            when(habitStreakRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID))
                    .thenReturn(Optional.empty());

            EnrichedHabit result = useCase.execute(HABIT_ID, USER_ID);

            assertFalse(result.completedToday());
            assertNull(result.todayLogId());
            assertEquals(0, result.currentStreak());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class, () -> useCase.execute(HABIT_ID, USER_ID));
        }
    }
}
