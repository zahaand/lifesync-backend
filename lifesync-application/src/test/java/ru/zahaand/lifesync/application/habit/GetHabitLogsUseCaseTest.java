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
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitLogsUseCaseTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;

    private GetHabitLogsUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        useCase = new GetHabitLogsUseCase(habitRepository, habitLogRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated habit logs")
        void shouldReturnPaginatedLogs() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            HabitLogRepository.HabitLogPage page = new HabitLogRepository.HabitLogPage(
                    List.of(), 0, 0, 0, 20);
            when(habitLogRepository.findByHabitIdAndUserId(HABIT_ID, USER_ID, 0, 20)).thenReturn(page);

            HabitLogRepository.HabitLogPage result = useCase.execute(HABIT_ID, USER_ID, 0, 20);

            assertEquals(0, result.totalElements());
            verify(habitLogRepository).findByHabitIdAndUserId(HABIT_ID, USER_ID, 0, 20);
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when habit not found")
        void shouldThrowWhenHabitNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class,
                    () -> useCase.execute(HABIT_ID, USER_ID, 0, 20));
        }
    }
}
