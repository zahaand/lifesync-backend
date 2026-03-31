package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalHabitLinkNotFoundException;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;
import ru.zahaand.lifesync.domain.habit.HabitId;

import java.util.UUID;

public class UnlinkHabitFromGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(UnlinkHabitFromGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalHabitLinkRepository habitLinkRepository;
    private final RecalculateGoalProgressUseCase recalculateGoalProgressUseCase;

    public UnlinkHabitFromGoalUseCase(GoalRepository goalRepository,
                                      GoalHabitLinkRepository habitLinkRepository,
                                      RecalculateGoalProgressUseCase recalculateGoalProgressUseCase) {
        this.goalRepository = goalRepository;
        this.habitLinkRepository = habitLinkRepository;
        this.recalculateGoalProgressUseCase = recalculateGoalProgressUseCase;
    }

    @Transactional
    public void execute(GoalId goalId, UUID userId, HabitId habitId) {
        log.debug("Unlinking habit {} from goal {}, userId={}", habitId.value(), goalId.value(), userId);

        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        if (!habitLinkRepository.existsByGoalIdAndHabitId(goalId, habitId)) {
            throw new GoalHabitLinkNotFoundException(
                    "Habit " + habitId.value() + " is not linked to goal " + goalId.value());
        }

        habitLinkRepository.deleteByGoalIdAndHabitId(goalId, habitId);

        int remainingLinks = habitLinkRepository.countTotalByGoalId(goalId);
        if (remainingLinks > 0) {
            recalculateGoalProgressUseCase.execute(goalId);
        }

        log.info("Habit unlinked: habitId={}, goalId={}", habitId.value(), goalId.value());
    }
}
