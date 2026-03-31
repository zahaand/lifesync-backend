package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.zahaand.lifesync.domain.goal.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecalculateGoalProgressUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalHabitLinkRepository habitLinkRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RecalculateGoalProgressUseCase useCase;
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = LocalDate.of(2026, 3, 1).atStartOfDay(ZoneId.of("UTC")).toInstant();
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new RecalculateGoalProgressUseCase(goalRepository, habitLinkRepository, eventPublisher, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should skip recalculation when no habits linked")
        void shouldSkipWhenNoHabits() {
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(0);

            useCase.execute(GOAL_ID);

            verify(goalRepository, never()).findById(any());
            verify(goalRepository, never()).update(any());
        }

        @Test
        @DisplayName("Should set progress to 0 when expected completions is 0")
        void shouldSetZeroWhenNoExpected() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 50, CREATED_AT, CREATED_AT, null);
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(1);
            when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.countCompletedDaysByGoalId(GOAL_ID)).thenReturn(0);
            when(habitLinkRepository.countExpectedCompletionsByGoalId(eq(GOAL_ID), any(), any())).thenReturn(0);

            useCase.execute(GOAL_ID);

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).update(captor.capture());
            assertEquals(0, captor.getValue().getProgress());
        }

        @Test
        @DisplayName("Should calculate progress using formula")
        void shouldCalculateProgress() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, CREATED_AT, CREATED_AT, null);
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(1);
            when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.countCompletedDaysByGoalId(GOAL_ID)).thenReturn(15);
            when(habitLinkRepository.countExpectedCompletionsByGoalId(eq(GOAL_ID), any(), any())).thenReturn(30);

            useCase.execute(GOAL_ID);

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).update(captor.capture());
            assertEquals(50, captor.getValue().getProgress());
        }

        @Test
        @DisplayName("Should cap progress at 100")
        void shouldCapAt100() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, CREATED_AT, CREATED_AT, null);
            when(habitLinkRepository.countTotalByGoalId(GOAL_ID)).thenReturn(1);
            when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(goal));
            when(habitLinkRepository.countCompletedDaysByGoalId(GOAL_ID)).thenReturn(50);
            when(habitLinkRepository.countExpectedCompletionsByGoalId(eq(GOAL_ID), any(), any())).thenReturn(30);

            useCase.execute(GOAL_ID);

            ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
            verify(goalRepository).update(captor.capture());
            assertEquals(100, captor.getValue().getProgress());
        }
    }
}
