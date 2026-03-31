package ru.zahaand.lifesync.domain.goal;

import java.time.Instant;
import java.util.Objects;

public final class GoalMilestone {

    private final GoalMilestoneId id;
    private final GoalId goalId;
    private final String title;
    private final int sortOrder;
    private final boolean completed;
    private final Instant completedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public GoalMilestone(GoalMilestoneId id, GoalId goalId, String title, int sortOrder,
                         boolean completed, Instant completedAt,
                         Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.goalId = Objects.requireNonNull(goalId, "goalId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.sortOrder = sortOrder;
        this.completed = completed;
        this.completedAt = completedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.deletedAt = deletedAt;
    }

    public GoalMilestone complete(Instant now) {
        return new GoalMilestone(id, goalId, title, sortOrder, true, now,
                createdAt, now, deletedAt);
    }

    public GoalMilestone uncomplete(Instant now) {
        return new GoalMilestone(id, goalId, title, sortOrder, false, null,
                createdAt, now, deletedAt);
    }

    public GoalMilestone update(String title, int sortOrder, Instant now) {
        return new GoalMilestone(id, goalId, title, sortOrder, completed, completedAt,
                createdAt, now, deletedAt);
    }

    public GoalMilestone softDelete(Instant now) {
        return new GoalMilestone(id, goalId, title, sortOrder, completed, completedAt,
                createdAt, now, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public GoalMilestoneId getId() {
        return id;
    }

    public GoalId getGoalId() {
        return goalId;
    }

    public String getTitle() {
        return title;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean getCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
