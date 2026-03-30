package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitStreak;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StreakCalculatorServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());

    private StreakCalculatorService service;

    private void setupClock(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(ZONE).toInstant(), ZONE);
        service = new StreakCalculatorService(clock);
    }

    @Nested
    class CalculateDaily {

        @Test
        @DisplayName("Should return streak of 0 for empty completions")
        void shouldReturnZeroForEmptyCompletions() {
            setupClock(LocalDate.of(2026, 3, 30));
            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, Collections.emptyList());
            assertEquals(0, streak.currentStreak());
            assertEquals(0, streak.longestStreak());
            assertTrue(streak.getLastLogDate().isEmpty());
        }

        @Test
        @DisplayName("Should calculate consecutive daily streak of 3")
        void shouldCalculateConsecutiveDailyStreak() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            List<LocalDate> logs = List.of(
                    today,
                    today.minusDays(1),
                    today.minusDays(2)
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, logs);
            assertEquals(3, streak.currentStreak());
            assertEquals(3, streak.longestStreak());
            assertEquals(today, streak.lastLogDate());
        }

        @Test
        @DisplayName("Should return 0 current streak when last log was 2+ days ago")
        void shouldReturnZeroWhenStreakBroken() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            List<LocalDate> logs = List.of(
                    today.minusDays(3),
                    today.minusDays(4),
                    today.minusDays(5)
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, logs);
            assertEquals(0, streak.currentStreak());
            assertEquals(3, streak.longestStreak());
        }

        @Test
        @DisplayName("Should keep streak alive when last log was yesterday")
        void shouldKeepStreakAliveWhenLastLogYesterday() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            List<LocalDate> logs = List.of(
                    today.minusDays(1),
                    today.minusDays(2),
                    today.minusDays(3)
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, logs);
            assertEquals(3, streak.currentStreak());
        }

        @Test
        @DisplayName("Should handle single completion")
        void shouldHandleSingleCompletion() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, List.of(today));
            assertEquals(1, streak.currentStreak());
            assertEquals(1, streak.longestStreak());
        }

        @Test
        @DisplayName("Should preserve longest streak after reset")
        void shouldPreserveLongestStreakAfterReset() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            // Longest was 5 consecutive (20-24 March), current is 2 (29-30 March)
            List<LocalDate> logs = List.of(
                    today,
                    today.minusDays(1),
                    // gap at 27, 26, 25
                    LocalDate.of(2026, 3, 24),
                    LocalDate.of(2026, 3, 23),
                    LocalDate.of(2026, 3, 22),
                    LocalDate.of(2026, 3, 21),
                    LocalDate.of(2026, 3, 20)
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.DAILY, null, logs);
            assertEquals(2, streak.currentStreak());
            assertEquals(5, streak.longestStreak());
        }
    }

    @Nested
    class CalculateWeekly {

        @Test
        @DisplayName("Should calculate consecutive weekly streak of 3")
        void shouldCalculateConsecutiveWeeklyStreak() {
            // Monday of current week: 2026-03-30 is Monday
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            List<LocalDate> logs = List.of(
                    today,                    // week 14
                    today.minusWeeks(1),     // week 13
                    today.minusWeeks(2)      // week 12
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.WEEKLY, null, logs);
            assertEquals(3, streak.currentStreak());
            assertEquals(3, streak.longestStreak());
        }

        @Test
        @DisplayName("Should break weekly streak on gap")
        void shouldBreakWeeklyStreakOnGap() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            List<LocalDate> logs = List.of(
                    today,
                    today.minusWeeks(2) // skipped last week
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.WEEKLY, null, logs);
            assertEquals(1, streak.currentStreak());
        }
    }

    @Nested
    class CalculateCustom {

        @Test
        @DisplayName("Should calculate streak for Mon/Wed/Fri pattern")
        void shouldCalculateCustomStreak() {
            // 2026-03-30 is Monday
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            DayOfWeekSet targetDays = new DayOfWeekSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

            List<LocalDate> logs = List.of(
                    today,                                        // Mon 30
                    LocalDate.of(2026, 3, 27),   // Fri 27
                    LocalDate.of(2026, 3, 25),   // Wed 25
                    LocalDate.of(2026, 3, 23)    // Mon 23
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.CUSTOM, targetDays, logs);
            assertEquals(4, streak.currentStreak());
            assertEquals(4, streak.longestStreak());
        }

        @Test
        @DisplayName("Should break custom streak on missed target day")
        void shouldBreakCustomStreakOnMissedTargetDay() {
            LocalDate today = LocalDate.of(2026, 3, 30);
            setupClock(today);

            DayOfWeekSet targetDays = new DayOfWeekSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

            // Missing Wednesday March 25
            List<LocalDate> logs = List.of(
                    today,                                        // Mon 30
                    LocalDate.of(2026, 3, 27),   // Fri 27
                    // gap: Wed 25 missing
                    LocalDate.of(2026, 3, 23)    // Mon 23
            );

            HabitStreak streak = service.calculate(HABIT_ID, Frequency.CUSTOM, targetDays, logs);
            assertEquals(2, streak.currentStreak()); // only Mon 30 + Fri 27
        }
    }
}
