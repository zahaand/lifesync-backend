package ru.zahaand.lifesync.domain.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository {

    Goal save(Goal goal);

    Optional<Goal> findById(GoalId id);

    Optional<Goal> findByIdAndUserId(GoalId id, UUID userId);

    GoalPage findAllByUserId(UUID userId, GoalStatus status, int page, int size);

    Goal update(Goal goal);

    record GoalPage(List<Goal> content, long totalElements, int totalPages, int page, int size) {
    }
}
