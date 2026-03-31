package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.util.UUID;

public class GetGoalsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetGoalsUseCase.class);

    private final GoalRepository goalRepository;

    public GetGoalsUseCase(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    public GoalRepository.GoalPage execute(UUID userId, GoalStatus status, int page, int size) {
        log.debug("Getting goals for userId={}, status={}, page={}, size={}", userId, status, page, size);
        return goalRepository.findAllByUserId(userId, status, page, size);
    }
}
