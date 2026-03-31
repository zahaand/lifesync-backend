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

public class UpdateMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateMilestoneUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final Clock clock;

    public UpdateMilestoneUseCase(GoalRepository goalRepository,
                                  GoalMilestoneRepository milestoneRepository,
                                  Clock clock) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.clock = clock;
    }

    @Transactional
    public GoalMilestone execute(GoalId goalId, UUID userId,
                                 GoalMilestoneId milestoneId, UpdateCommand command) {
        log.debug("Updating milestone id={}, goalId={}, userId={}", milestoneId.value(), goalId.value(), userId);

        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        GoalMilestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new GoalNotFoundException("Milestone not found: " + milestoneId.value()));

        Instant now = Instant.now(clock);
        GoalMilestone updated = milestone;

        if (command.completed() != null) {
            if (command.completed()) {
                updated = updated.complete(now);
            } else {
                updated = updated.uncomplete(now);
            }
        }

        String newTitle = command.title() != null ? command.title() : updated.getTitle();
        int newSortOrder = command.sortOrder() != null ? command.sortOrder() : updated.getSortOrder();
        updated = updated.update(newTitle, newSortOrder, now);

        GoalMilestone saved = milestoneRepository.update(updated);

        log.info("Milestone updated: id={}, goalId={}", milestoneId.value(), goalId.value());
        return saved;
    }

    public record UpdateCommand(
            String title,
            Integer sortOrder,
            Boolean completed
    ) {
    }
}
