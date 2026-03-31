package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public class RecalculateGoalProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecalculateGoalProgressUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalHabitLinkRepository habitLinkRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RecalculateGoalProgressUseCase(GoalRepository goalRepository,
                                          GoalHabitLinkRepository habitLinkRepository,
                                          ApplicationEventPublisher eventPublisher,
                                          Clock clock) {
        this.goalRepository = goalRepository;
        this.habitLinkRepository = habitLinkRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void execute(GoalId goalId) {
        log.debug("Recalculating progress for goalId={}", goalId.value());

        int totalLinked = habitLinkRepository.countTotalByGoalId(goalId);
        if (totalLinked == 0) {
            log.debug("No habits linked to goalId={}, skipping recalculation", goalId.value());
            return;
        }

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = goal.getTargetDate() != null && goal.getTargetDate().isBefore(today)
                ? goal.getTargetDate() : today;
        LocalDate createdAt = goal.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate();

        int completedDays = habitLinkRepository.countCompletedDaysByGoalId(goalId);
        int expectedCompletions = habitLinkRepository.countExpectedCompletionsByGoalId(goalId, createdAt, endDate);

        if (expectedCompletions == 0) {
            log.debug("Expected completions is 0 for goalId={}, setting progress to 0", goalId.value());
            Instant now = Instant.now(clock);
            Goal updated = goal.updateProgress(0, now);
            goalRepository.update(updated);
            return;
        }

        int progress = Math.min(Math.round((float) completedDays / expectedCompletions * 100), 100);

        Instant now = Instant.now(clock);
        Goal updated = goal.updateProgress(progress, now);
        goalRepository.update(updated);

        GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                UUID.randomUUID().toString(),
                goalId.value(),
                goal.getUserId(),
                null,
                progress,
                now
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish GoalProgressUpdatedEvent: goalId={}, error={}",
                    goalId.value(), e.getMessage(), e);
        }

        log.info("Goal progress recalculated: goalId={}, progress={}", goalId.value(), progress);
    }
}
