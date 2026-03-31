package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.goal.*;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateGoalUseCaseTest {

    @Mock
    private GoalRepository goalRepository;

    private UpdateGoalUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new UpdateGoalUseCase(goalRepository, CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should update goal title")
        void shouldUpdateTitle() {
            Instant now = Instant.now();
            Goal existing = new Goal(GOAL_ID, USER_ID, "Old", "desc", null,
                    GoalStatus.ACTIVE, 0, now, now, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(existing));
            when(goalRepository.update(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

            UpdateGoalUseCase.UpdateCommand cmd = new UpdateGoalUseCase.UpdateCommand(
                    "New", null, false, null, false, null);
            Goal result = useCase.execute(GOAL_ID, USER_ID, cmd);

            assertEquals("New", result.getTitle());
            assertEquals("desc", result.getDescription());
        }

        @Test
        @DisplayName("Should throw GoalNotFoundException for missing goal")
        void shouldThrowWhenNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

            UpdateGoalUseCase.UpdateCommand cmd = new UpdateGoalUseCase.UpdateCommand(
                    "X", null, false, null, false, null);

            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, cmd));
        }
    }
}
