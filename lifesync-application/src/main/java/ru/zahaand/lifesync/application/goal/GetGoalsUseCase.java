package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneRepository;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GetGoalsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetGoalsUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;

    public GetGoalsUseCase(GoalRepository goalRepository,
                           GoalMilestoneRepository milestoneRepository) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
    }

    public EnrichedGoalPage execute(UUID userId, GoalStatus status, int page, int size) {
        log.debug("Getting goals for userId={}, status={}, page={}, size={}", userId, status, page, size);

        GoalRepository.GoalPage goalPage = goalRepository.findAllByUserId(userId, status, page, size);

        List<GoalId> goalIds = goalPage.content().stream()
                .map(Goal::getId)
                .toList();

        Map<GoalId, List<GoalMilestone>> milestonesMap = milestoneRepository.findFirst3ActiveByGoalIds(goalIds);

        List<EnrichedGoal> enriched = goalPage.content().stream()
                .map(goal -> new EnrichedGoal(goal, milestonesMap.getOrDefault(goal.getId(), List.of())))
                .toList();

        return new EnrichedGoalPage(enriched, goalPage.totalElements(), goalPage.totalPages(),
                goalPage.page(), goalPage.size());
    }

    public record EnrichedGoal(Goal goal, List<GoalMilestone> milestones) {
    }

    public record EnrichedGoalPage(List<EnrichedGoal> content, long totalElements,
                                   int totalPages, int page, int size) {
    }
}
