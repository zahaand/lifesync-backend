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
import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGoalUseCaseTest {

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private GoalMilestoneRepository milestoneRepository;
    @Mock
    private GoalHabitLinkRepository habitLinkRepository;

    private GetGoalUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new GetGoalUseCase(goalRepository, milestoneRepository, habitLinkRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return goal detail with milestones and habit links")
        void shouldReturnGoalDetail() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Test", null, null,
                    GoalStatus.ACTIVE, 0, NOW, NOW, null);
            GoalMilestone milestone = new GoalMilestone(new GoalMilestoneId(UUID.randomUUID()),
                    GOAL_ID, "Step 1", 0, false, null, NOW, NOW, null);
            GoalHabitLink link = new GoalHabitLink(new GoalHabitLinkId(UUID.randomUUID()),
                    GOAL_ID, new HabitId(UUID.randomUUID()), NOW, NOW);

            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(milestoneRepository.findAllActiveByGoalId(GOAL_ID)).thenReturn(List.of(milestone));
            when(habitLinkRepository.findAllByGoalId(GOAL_ID)).thenReturn(List.of(link));

            GetGoalUseCase.GoalDetail detail = useCase.execute(GOAL_ID, USER_ID);

            assertEquals(goal, detail.goal());
            assertEquals(1, detail.milestones().size());
            assertEquals(1, detail.habitLinks().size());
        }

        @Test
        @DisplayName("Should throw GoalNotFoundException when goal not found")
        void shouldThrowWhenNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID));
        }
    }
}
