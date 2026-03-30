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
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteHabitUseCaseTest {

    @Mock
    private HabitRepository habitRepository;

    private DeleteHabitUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new DeleteHabitUseCase(habitRepository, CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should soft-delete habit successfully")
        void shouldSoftDeleteHabit() {
            Habit habit = new Habit(HABIT_ID, USER_ID, "Test", null, Frequency.DAILY,
                    null, null, true, Instant.now(), Instant.now(), null);
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));

            useCase.execute(HABIT_ID, USER_ID);

            ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
            verify(habitRepository).update(captor.capture());
            assertTrue(captor.getValue().isDeleted());
            assertNotNull(captor.getValue().getDeletedAt());
        }

        @Test
        @DisplayName("Should throw HabitNotFoundException when habit not found")
        void shouldThrowWhenNotFound() {
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(HabitNotFoundException.class, () -> useCase.execute(HABIT_ID, USER_ID));
        }
    }
}
