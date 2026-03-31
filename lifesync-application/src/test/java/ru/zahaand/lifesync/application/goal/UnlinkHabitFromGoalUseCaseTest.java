package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.goal.*;
import ru.zahaand.lifesync.domain.goal.exception.GoalHabitLinkNotFoundException;
import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnlinkHabitFromGoalUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalHabitLinkRepository habitLinkRepository;
    @Mock private RecalculateGoalProgressUseCase recalculateGoalProgressUseCase;

    private UnlinkHabitFromGoalUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new UnlinkHabitFromGoalUseCase(goalRepository, habitLinkRepository, recalculateGoalProgressUseCase);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should unlink and recalculate when remaining links exist")
        void shouldUnlinkAndRecalculate() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.existsByGoalIdAndHabitId(GOAL_ID, HABIT_ID)).thenReturn(true);
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(1);

            useCase.execute(GOAL_ID, USER_ID, HABIT_ID);

            verify(habitLinkRepository).deleteByGoalIdAndHabitId(GOAL_ID, HABIT_ID);
            verify(recalculateGoalProgressUseCase).execute(GOAL_ID);
        }

        @Test
        @DisplayName("Should skip recalculation when no remaining links")
        void shouldSkipRecalculationWhenNoLinks() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.existsByGoalIdAndHabitId(GOAL_ID, HABIT_ID)).thenReturn(true);
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(0);

            useCase.execute(GOAL_ID, USER_ID, HABIT_ID);

            verify(habitLinkRepository).deleteByGoalIdAndHabitId(GOAL_ID, HABIT_ID);
            verifyNoInteractions(recalculateGoalProgressUseCase);
        }

        @Test
        @DisplayName("Should throw when link not found")
        void shouldThrowWhenLinkNotFound() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.existsByGoalIdAndHabitId(GOAL_ID, HABIT_ID)).thenReturn(false);

            assertThrows(GoalHabitLinkNotFoundException.class,
                    () -> useCase.execute(GOAL_ID, USER_ID, HABIT_ID));
        }
    }
}
