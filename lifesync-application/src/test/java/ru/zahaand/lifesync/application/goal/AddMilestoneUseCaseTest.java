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
class AddMilestoneUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalMilestoneRepository milestoneRepository;

    private AddMilestoneUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new AddMilestoneUseCase(goalRepository, milestoneRepository, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should add milestone to existing goal")
        void shouldAddMilestone() {
            Instant now = Instant.now();
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, now, now, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.save(any(GoalMilestone.class))).thenAnswer(i -> i.getArgument(0));

            GoalMilestone result = useCase.execute(GOAL_ID, USER_ID, "Step 1", 1);
            assertEquals("Step 1", result.getTitle());
            assertEquals(1, result.getSortOrder());
            assertFalse(result.getCompleted());
        }

        @Test
        @DisplayName("Should throw GoalNotFoundException when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, "Step", 0));
        }
    }
}
