package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CreateGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final Clock clock;

    public CreateGoalUseCase(GoalRepository goalRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.clock = clock;
    }

    @Transactional
    public Goal execute(UUID userId, String title, String description, LocalDate targetDate) {
        log.debug("Creating goal for userId={}, title={}", userId, title);

        Instant now = Instant.now(clock);
        GoalId goalId = new GoalId(UUID.randomUUID());

        Goal goal = new Goal(goalId, userId, title, description, targetDate,
                GoalStatus.ACTIVE, 0, now, now, null);

        Goal saved = goalRepository.save(goal);

        log.info("Goal created: id={}, userId={}", goalId.value(), userId);
        return saved;
    }
}
