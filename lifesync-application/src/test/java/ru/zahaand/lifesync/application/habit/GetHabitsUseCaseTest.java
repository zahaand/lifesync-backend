package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitsUseCaseTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    @Mock
    private HabitStreakRepository habitStreakRepository;

    private GetHabitsUseCase useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 3);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new GetHabitsUseCase(habitRepository, habitLogRepository, habitStreakRepository, FIXED_CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated habit list with enrichment data")
        void shouldReturnPaginatedListWithEnrichment() {
            HabitId habitId = new HabitId(UUID.randomUUID());
            Habit habit = new Habit(habitId, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);

            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(habit), 1, 1, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, "active", 0, 20)).thenReturn(page);

            HabitLogId logId = new HabitLogId(UUID.randomUUID());
            HabitLog todayLog = new HabitLog(logId, habitId, USER_ID, TODAY, null,
                    Instant.now(), Instant.now(), null);
            when(habitLogRepository.findTodayLogsByHabitIds(List.of(habitId), TODAY))
                    .thenReturn(Map.of(habitId, todayLog));

            HabitStreak streak = new HabitStreak(habitId, 5, 10, TODAY);
            when(habitStreakRepository.findByHabitIdsAndUserId(List.of(habitId), USER_ID))
                    .thenReturn(Map.of(habitId, streak));

            GetHabitsUseCase.EnrichedHabitPage result = useCase.execute(USER_ID, "active", 0, 20);

            assertEquals(1, result.totalElements());
            assertEquals(1, result.content().size());

            EnrichedHabit enriched = result.content().get(0);
            assertTrue(enriched.completedToday());
            assertEquals(logId, enriched.todayLogId());
            assertEquals(5, enriched.currentStreak());
        }

        @Test
        @DisplayName("Should return empty page without calling batch queries")
        void shouldReturnEmptyPage() {
            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(), 0, 0, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, "active", 0, 20)).thenReturn(page);

            GetHabitsUseCase.EnrichedHabitPage result = useCase.execute(USER_ID, "active", 0, 20);

            assertEquals(0, result.totalElements());
            assertTrue(result.content().isEmpty());
        }

        @Test
        @DisplayName("Should set completedToday to false when no log exists for today")
        void shouldSetCompletedTodayFalseWhenNoLog() {
            HabitId habitId = new HabitId(UUID.randomUUID());
            Habit habit = new Habit(habitId, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);

            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(habit), 1, 1, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, null, 0, 20)).thenReturn(page);
            when(habitLogRepository.findTodayLogsByHabitIds(List.of(habitId), TODAY))
                    .thenReturn(Map.of());
            when(habitStreakRepository.findByHabitIdsAndUserId(List.of(habitId), USER_ID))
                    .thenReturn(Map.of());

            GetHabitsUseCase.EnrichedHabitPage result = useCase.execute(USER_ID, null, 0, 20);

            EnrichedHabit enriched = result.content().get(0);
            assertFalse(enriched.completedToday());
            assertNull(enriched.todayLogId());
            assertEquals(0, enriched.currentStreak());
        }

        @Test
        @DisplayName("Should pass userId to repository")
        void shouldPassUserId() {
            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(), 0, 0, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, null, 0, 20)).thenReturn(page);

            useCase.execute(USER_ID, null, 0, 20);

            verify(habitRepository).findAllByUserId(USER_ID, null, 0, 20);
        }
    }
}
