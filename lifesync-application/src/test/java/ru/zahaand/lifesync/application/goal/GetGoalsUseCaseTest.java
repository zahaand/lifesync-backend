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
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGoalsUseCaseTest {

    @Mock
    private GoalRepository goalRepository;

    private GetGoalsUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetGoalsUseCase(goalRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated goals")
        void shouldReturnPaginatedGoals() {
            Instant now = Instant.now();
            Goal goal = new Goal(new GoalId(UUID.randomUUID()), USER_ID, "Goal 1", null,
                    null, GoalStatus.ACTIVE, 0, now, now, null);
            GoalRepository.GoalPage page = new GoalRepository.GoalPage(
                    List.of(goal), 1, 1, 0, 20);

            when(goalRepository.findAllByUserId(USER_ID, GoalStatus.ACTIVE, 0, 20))
                    .thenReturn(page);

            GoalRepository.GoalPage result = useCase.execute(USER_ID, GoalStatus.ACTIVE, 0, 20);

            assertEquals(1, result.content().size());
            assertEquals(1, result.totalElements());
        }
    }
}
