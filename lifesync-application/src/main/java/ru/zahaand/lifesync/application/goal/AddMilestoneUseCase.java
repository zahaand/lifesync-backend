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

public class AddMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(AddMilestoneUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final Clock clock;

    public AddMilestoneUseCase(GoalRepository goalRepository,
                               GoalMilestoneRepository milestoneRepository,
                               Clock clock) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.clock = clock;
    }

    @Transactional
    public GoalMilestone execute(GoalId goalId, UUID userId, String title, int sortOrder) {
        log.debug("Adding milestone to goal id={}, userId={}, title={}", goalId.value(), userId, title);

        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        Instant now = Instant.now(clock);
        GoalMilestoneId milestoneId = new GoalMilestoneId(UUID.randomUUID());

        GoalMilestone milestone = new GoalMilestone(milestoneId, goalId, title, sortOrder,
                false, null, now, now, null);

        GoalMilestone saved = milestoneRepository.save(milestone);

        log.info("Milestone added: id={}, goalId={}", milestoneId.value(), goalId.value());
        return saved;
    }
}
