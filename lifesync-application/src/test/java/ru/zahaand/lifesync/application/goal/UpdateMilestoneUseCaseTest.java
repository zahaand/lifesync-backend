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
class UpdateMilestoneUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalMilestoneRepository milestoneRepository;

    private UpdateMilestoneUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final GoalMilestoneId MILESTONE_ID = new GoalMilestoneId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new UpdateMilestoneUseCase(goalRepository, milestoneRepository, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should mark milestone as completed")
        void shouldMarkCompleted() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            GoalMilestone milestone = new GoalMilestone(MILESTONE_ID, GOAL_ID, "Step", 0, false, null, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.findByIdAndGoalId(MILESTONE_ID, GOAL_ID)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.update(any(GoalMilestone.class))).thenAnswer(i -> i.getArgument(0));

            UpdateMilestoneUseCase.UpdateCommand cmd = new UpdateMilestoneUseCase.UpdateCommand(null, null, true);
            GoalMilestone result = useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID, cmd);

            assertTrue(result.getCompleted());
            assertNotNull(result.getCompletedAt());
        }

        @Test
        @DisplayName("Should update milestone title")
        void shouldUpdateTitle() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            GoalMilestone milestone = new GoalMilestone(MILESTONE_ID, GOAL_ID, "Old", 0, false, null, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.findByIdAndGoalId(MILESTONE_ID, GOAL_ID)).thenReturn(Optional.of(milestone));
            when(milestoneRepository.update(any(GoalMilestone.class))).thenAnswer(i -> i.getArgument(0));

            UpdateMilestoneUseCase.UpdateCommand cmd = new UpdateMilestoneUseCase.UpdateCommand("New", null, null);
            GoalMilestone result = useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID, cmd);

            assertEquals("New", result.getTitle());
        }

        @Test
        @DisplayName("Should throw when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
            UpdateMilestoneUseCase.UpdateCommand cmd = new UpdateMilestoneUseCase.UpdateCommand("X", null, null);
            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID, cmd));
        }

        @Test
        @DisplayName("Should throw when milestone not found")
        void shouldThrowWhenMilestoneNotFound() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.findByIdAndGoalId(MILESTONE_ID, GOAL_ID)).thenReturn(Optional.empty());
            UpdateMilestoneUseCase.UpdateCommand cmd = new UpdateMilestoneUseCase.UpdateCommand("X", null, null);
            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, MILESTONE_ID, cmd));
        }
    }
}
