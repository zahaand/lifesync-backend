package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalHabitLink;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneRepository;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;
import ru.zahaand.lifesync.domain.habit.HabitId;

import java.util.List;
import java.util.UUID;

public class GetGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final GoalHabitLinkRepository habitLinkRepository;

    public GetGoalUseCase(GoalRepository goalRepository,
                          GoalMilestoneRepository milestoneRepository,
                          GoalHabitLinkRepository habitLinkRepository) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.habitLinkRepository = habitLinkRepository;
    }

    public GoalDetail execute(GoalId goalId, UUID userId) {
        log.debug("Getting goal id={}, userId={}", goalId.value(), userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        List<GoalMilestone> milestones = milestoneRepository.findAllActiveByGoalId(goalId);
        List<HabitId> linkedHabitIds = habitLinkRepository.findAllByGoalId(goalId).stream()
                .map(GoalHabitLink::getHabitId)
                .toList();

        return new GoalDetail(goal, milestones, linkedHabitIds);
    }

    public record GoalDetail(Goal goal, List<GoalMilestone> milestones, List<HabitId> linkedHabitIds) {
    }
}
