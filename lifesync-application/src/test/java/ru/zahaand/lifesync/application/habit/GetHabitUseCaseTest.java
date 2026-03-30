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
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;

    private GetHabitUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        useCase = new GetHabitUseCase(habitRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return habit when found")
        void shouldReturnHabitWhenFound() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            Habit result = useCase.execute(HABIT_ID, USER_ID);

            assertEquals("Test", result.getTitle());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class, () -> useCase.execute(HABIT_ID, USER_ID));
        }
    }
}
