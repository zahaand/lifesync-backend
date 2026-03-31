package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteMilestoneUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalMilestoneRepository milestoneRepository;

    private DeleteMilestoneUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final GoalMilestoneId MILESTONE_ID = new GoalMilestoneId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new DeleteMilestoneUseCase(goalRepository, milestoneRepository, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should soft-delete milestone")
        void shouldSoftDelete() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            GoalMilestone milestone = new GoalMilestone(MILESTONE_ID, GOAL_ID, "Step", 0, false, null, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.findByIdAndGoalId(MILESTONE_ID, GOAL_ID)).thenReturn(Optional.of(milestone));

            useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID);

            ArgumentCaptor<GoalMilestone> captor = ArgumentCaptor.forClass(GoalMilestone.class);
            verify(milestoneRepository).update(captor.capture());
            assertTrue(captor.getValue().isDeleted());
        }

        @Test
        @DisplayName("Should throw when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID));
        }
    }
}
