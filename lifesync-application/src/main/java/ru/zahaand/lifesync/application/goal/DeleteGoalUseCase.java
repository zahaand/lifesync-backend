package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class DeleteGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final Clock clock;

    public DeleteGoalUseCase(GoalRepository goalRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(GoalId goalId, UUID userId) {
        log.debug("Deleting goal id={}, userId={}", goalId.value(), userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        Instant now = Instant.now(clock);
        Goal deleted = goal.softDelete(now);
        goalRepository.update(deleted);

        log.info("Goal deleted: id={}, userId={}", goalId.value(), userId);
    }
}
