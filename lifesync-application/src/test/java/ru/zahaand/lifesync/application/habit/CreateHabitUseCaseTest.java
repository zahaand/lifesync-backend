package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitStreakRepository habitStreakRepository;

    private CreateHabitUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new CreateHabitUseCase(habitRepository, habitStreakRepository, CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should create a DAILY habit successfully")
        void shouldCreateDailyHabit() {
            when(habitRepository.save(any(Habit.class))).thenAnswer(i -> i.getArgument(0));
            when(habitStreakRepository.save(any(HabitStreak.class))).thenAnswer(i -> i.getArgument(0));

            Habit result = useCase.execute(USER_ID, "Morning run", "5km", Frequency.DAILY, null, null);

            assertNotNull(result);
            assertEquals("Morning run", result.getTitle());
            assertEquals(Frequency.DAILY, result.getFrequency());
            assertTrue(result.isActive());

            ArgumentCaptor<HabitStreak> streakCaptor = ArgumentCaptor.forClass(HabitStreak.class);
            verify(habitStreakRepository).save(streakCaptor.capture());
            assertEquals(0, streakCaptor.getValue().currentStreak());
            assertEquals(0, streakCaptor.getValue().longestStreak());
        }

        @Test
        @DisplayName("Should create a CUSTOM habit with target days")
        void shouldCreateCustomHabit() {
            when(habitRepository.save(any(Habit.class))).thenAnswer(i -> i.getArgument(0));
            when(habitStreakRepository.save(any(HabitStreak.class))).thenAnswer(i -> i.getArgument(0));

            DayOfWeekSet days = new DayOfWeekSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
            Habit result = useCase.execute(USER_ID, "Gym", null, Frequency.CUSTOM, days, null);

            assertEquals(Frequency.CUSTOM, result.getFrequency());
            assertNotNull(result.getTargetDaysOfWeek());
        }

        @Test
        @DisplayName("Should throw when CUSTOM frequency has no target days")
        void shouldThrowWhenCustomWithoutTargetDays() {
            assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute(USER_ID, "Gym", null, Frequency.CUSTOM, null, null));
        }
    }
}
