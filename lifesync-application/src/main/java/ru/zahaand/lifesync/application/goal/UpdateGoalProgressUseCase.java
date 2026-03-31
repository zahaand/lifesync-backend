package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class UpdateGoalProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateGoalProgressUseCase.class);

    private final GoalRepository goalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UpdateGoalProgressUseCase(GoalRepository goalRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.goalRepository = goalRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Goal execute(GoalId goalId, UUID userId, int progress) {
        log.debug("Manually updating goal progress id={}, userId={}, progress={}", goalId.value(), userId, progress);

        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("Progress must be between 0 and 100, got: " + progress);
        }

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        Instant now = Instant.now(clock);
        Goal updated = goal.updateProgress(progress, now);
        Goal saved = goalRepository.update(updated);

        GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                UUID.randomUUID().toString(),
                goalId.value(),
                userId,
                null,
                progress,
                now
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish GoalProgressUpdatedEvent: goalId={}, userId={}, error={}",
                    goalId.value(), userId, e.getMessage(), e);
        }

        log.info("Goal progress updated manually: id={}, progress={}", goalId.value(), progress);
        return saved;
    }
}
