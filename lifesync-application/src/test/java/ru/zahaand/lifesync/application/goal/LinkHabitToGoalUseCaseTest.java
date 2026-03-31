package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.goal.*;
import ru.zahaand.lifesync.domain.goal.exception.DuplicateGoalHabitLinkException;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkHabitToGoalUseCaseTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalHabitLinkRepository habitLinkRepository;
    @Mock private HabitRepository habitRepository;
    @Mock private RecalculateGoalProgressUseCase recalculateGoalProgressUseCase;
    @Mock private Habit mockHabit;

    private LinkHabitToGoalUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final HabitId HABIT_ID = new HabitId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new LinkHabitToGoalUseCase(goalRepository, habitLinkRepository,
                habitRepository, recalculateGoalProgressUseCase, CLOCK);
    }

    @Nested
    class Execute {
        @Test
        @DisplayName("Should link habit to goal and trigger recalculation")
        void shouldLinkAndRecalculate() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(mockHabit));
            when(habitLinkRepository.existsByGoalIdAndHabitId(GOAL_ID, HABIT_ID)).thenReturn(false);
            when(habitLinkRepository.save(any(GoalHabitLink.class))).thenAnswer(i -> i.getArgument(0));

            GoalHabitLink result = useCase.execute(GOAL_ID, USER_ID, HABIT_ID);

            assertNotNull(result);
            assertEquals(GOAL_ID, result.getGoalId());
            assertEquals(HABIT_ID, result.getHabitId());
            verify(recalculateGoalProgressUseCase).execute(GOAL_ID);
        }

        @Test
        @DisplayName("Should reject duplicate link")
        void shouldRejectDuplicate() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.of(mockHabit));
            when(habitLinkRepository.existsByGoalIdAndHabitId(GOAL_ID, HABIT_ID)).thenReturn(true);

            assertThrows(DuplicateGoalHabitLinkException.class,
                    () -> useCase.execute(GOAL_ID, USER_ID, HABIT_ID));
        }

        @Test
        @DisplayName("Should throw when goal not found")
        void shouldThrowWhenGoalNotFound() {
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
            assertThrows(GoalNotFoundException.class,
                    () -> useCase.execute(GOAL_ID, USER_ID, HABIT_ID));
        }

        @Test
        @DisplayName("Should throw when habit not found")
        void shouldThrowWhenHabitNotFound() {
            Goal goal = new Goal(GOAL_ID, USER_ID, "Goal", null, null, GoalStatus.ACTIVE, 0, NOW, NOW, null);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
            when(habitRepository.findByIdAndUserId(HABIT_ID, USER_ID)).thenReturn(Optional.empty());
            assertThrows(HabitNotFoundException.class,
                    () -> useCase.execute(GOAL_ID, USER_ID, HABIT_ID));
        }
    }
}
