package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class UpdateGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final Clock clock;

    public UpdateGoalUseCase(GoalRepository goalRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.clock = clock;
    }

    @Transactional
    public Goal execute(GoalId goalId, UUID userId, UpdateCommand command) {
        log.debug("Updating goal id={}, userId={}", goalId.value(), userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        String newTitle = command.title() != null ? command.title() : goal.getTitle();
        String newDescription = command.descriptionProvided() ? command.description() : goal.getDescription();
        LocalDate newTargetDate = command.targetDateProvided() ? command.targetDate() : goal.getTargetDate();
        GoalStatus newStatus = command.status() != null ? command.status() : goal.getStatus();

        Instant now = Instant.now(clock);
        Goal updated = goal.update(newTitle, newDescription, newTargetDate, newStatus, now);
        Goal saved = goalRepository.update(updated);

        log.info("Goal updated: id={}, userId={}", goalId.value(), userId);
        return saved;
    }

    public record UpdateCommand(
            String title,
            String description,
            boolean descriptionProvided,
            LocalDate targetDate,
            boolean targetDateProvided,
            GoalStatus status
    ) {
    }
}
