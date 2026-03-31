package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateGoalProgressUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UpdateGoalProgressUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new UpdateGoalProgressUseCase(goalRepository, eventPublisher, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should update progress to 50")
        void shouldUpdateProgress() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(goalRepository.update(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

            Goal result = useCase.execute(GOAL_ID, USER_ID, 50);

            assertEquals(50, result.getProgress());
            assertEquals(GoalStatus.ACTIVE, result.getStatus());
            verify(eventPublisher).publishEvent(any(GoalProgressUpdatedEvent.class));
        }

        @Test
        @DisplayName("Should set COMPLETED when progress is 100")
        void shouldSetCompletedAt100() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(goalRepository.update(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

            Goal result = useCase.execute(GOAL_ID, USER_ID, 100);

            assertEquals(100, result.getProgress());
            assertEquals(GoalStatus.COMPLETED, result.getStatus());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for progress > 100")
        void shouldThrowForInvalidProgress() {
            assertThrows(IllegalArgumentException.class, () -> useCase.execute(GOAL_ID, USER_ID, 150));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for progress < 0")
        void shouldThrowForNegativeProgress() {
            assertThrows(IllegalArgumentException.class, () -> useCase.execute(GOAL_ID, USER_ID, -1));
        }

        @Test
        @DisplayName("Should not fail if event publishing throws")
        void shouldNotFailOnEventError() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(goalRepository.update(any(Goal.class))).thenAnswer(i -> i.getArgument(0));
            doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishEvent(any());

            Goal result = useCase.execute(GOAL_ID, USER_ID, 50);
            assertEquals(50, result.getProgress());
        }

        @Test
        @DisplayName("Should throw GoalNotFoundException for missing goal")
        void shouldThrowWhenNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
            assertThrows(GoalNotFoundException.class, () -> useCase.execute(GOAL_ID, USER_ID, 50));
        }
    }
}
