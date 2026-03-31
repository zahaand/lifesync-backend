package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneId;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneRepository;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class DeleteMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteMilestoneUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final Clock clock;

    public DeleteMilestoneUseCase(GoalRepository goalRepository,
                                  GoalMilestoneRepository milestoneRepository,
                                  Clock clock) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(GoalId goalId, UUID userId, GoalMilestoneId milestoneId) {
        log.debug("Deleting milestone id={}, goalId={}, userId={}", milestoneId.value(), goalId.value(), userId);

        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        GoalMilestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new GoalNotFoundException("Milestone not found: " + milestoneId.value()));

        Instant now = Instant.now(clock);
        GoalMilestone deleted = milestone.softDelete(now);
        milestoneRepository.update(deleted);

        log.info("Milestone deleted: id={}, goalId={}", milestoneId.value(), goalId.value());
    }
}
