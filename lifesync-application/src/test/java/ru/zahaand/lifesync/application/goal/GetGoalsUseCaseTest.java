package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneId;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneRepository;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGoalsUseCaseTest {

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private GoalMilestoneRepository milestoneRepository;

    private GetGoalsUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetGoalsUseCase(goalRepository, milestoneRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated goals with milestones")
        void shouldReturnPaginatedGoalsWithMilestones() {
            Instant now = Instant.now();
            GoalId goalId = new GoalId(UUID.randomUUID());
            Goal goal = new Goal(goalId, USER_ID, "Goal 1", null,
                    null, GoalStatus.ACTIVE, 0, now, now, null);
            GoalRepository.GoalPage page = new GoalRepository.GoalPage(
                    List.of(goal), 1, 1, 0, 20);

            GoalMilestone milestone = new GoalMilestone(new GoalMilestoneId(UUID.randomUUID()),
                    goalId, "Step 1", 0, false, null, now, now, null);

            when(goalRepository.findAllByUserId(USER_ID, GoalStatus.ACTIVE, 0, 20))
                    .thenReturn(page);
            when(milestoneRepository.findFirst3ActiveByGoalIds(List.of(goalId)))
                    .thenReturn(Map.of(goalId, List.of(milestone)));

            GetGoalsUseCase.EnrichedGoalPage result = useCase.execute(USER_ID, GoalStatus.ACTIVE, 0, 20);

            assertEquals(1, result.content().size());
            assertEquals(1, result.totalElements());
            assertEquals(1, result.content().get(0).milestones().size());
            assertEquals("Step 1", result.content().get(0).milestones().get(0).getTitle());
        }

        @Test
        @DisplayName("Should return empty milestones list when goal has no milestones")
        void shouldReturnEmptyMilestonesWhenNone() {
            Instant now = Instant.now();
            GoalId goalId = new GoalId(UUID.randomUUID());
            Goal goal = new Goal(goalId, USER_ID, "Goal 1", null,
                    null, GoalStatus.ACTIVE, 0, now, now, null);
            GoalRepository.GoalPage page = new GoalRepository.GoalPage(
                    List.of(goal), 1, 1, 0, 20);

            when(goalRepository.findAllByUserId(USER_ID, GoalStatus.ACTIVE, 0, 20))
                    .thenReturn(page);
            when(milestoneRepository.findFirst3ActiveByGoalIds(List.of(goalId)))
                    .thenReturn(Map.of());

            GetGoalsUseCase.EnrichedGoalPage result = useCase.execute(USER_ID, GoalStatus.ACTIVE, 0, 20);

            assertEquals(1, result.content().size());
            assertTrue(result.content().get(0).milestones().isEmpty());
        }

        @Test
        @DisplayName("Should return only first 3 milestones per goal")
        void shouldReturnOnlyFirst3Milestones() {
            Instant now = Instant.now();
            GoalId goalId = new GoalId(UUID.randomUUID());
            Goal goal = new Goal(goalId, USER_ID, "Goal 1", null,
                    null, GoalStatus.ACTIVE, 0, now, now, null);
            GoalRepository.GoalPage page = new GoalRepository.GoalPage(
                    List.of(goal), 1, 1, 0, 20);

            List<GoalMilestone> threeMilestones = List.of(
                    new GoalMilestone(new GoalMilestoneId(UUID.randomUUID()), goalId, "M1", 0, false, null, now, now, null),
                    new GoalMilestone(new GoalMilestoneId(UUID.randomUUID()), goalId, "M2", 1, false, null, now, now, null),
                    new GoalMilestone(new GoalMilestoneId(UUID.randomUUID()), goalId, "M3", 2, false, null, now, now, null)
            );

            when(goalRepository.findAllByUserId(USER_ID, GoalStatus.ACTIVE, 0, 20))
                    .thenReturn(page);
            when(milestoneRepository.findFirst3ActiveByGoalIds(List.of(goalId)))
                    .thenReturn(Map.of(goalId, threeMilestones));

            GetGoalsUseCase.EnrichedGoalPage result = useCase.execute(USER_ID, GoalStatus.ACTIVE, 0, 20);

            assertEquals(3, result.content().get(0).milestones().size());
            assertEquals("M1", result.content().get(0).milestones().get(0).getTitle());
            assertEquals("M3", result.content().get(0).milestones().get(2).getTitle());
        }
    }
}
