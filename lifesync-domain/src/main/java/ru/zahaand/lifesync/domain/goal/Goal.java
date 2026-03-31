package ru.zahaand.lifesync.domain.goal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Goal {

    private final GoalId id;
    private final UUID userId;
    private final String title;
    private final String description;
    private final LocalDate targetDate;
    private final GoalStatus status;
    private final int progress;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public Goal(GoalId id, UUID userId, String title, String description,
                LocalDate targetDate, GoalStatus status, int progress,
                Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        this.targetDate = targetDate;
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress must be between 0 and 100, got: " + progress);
        }
        this.progress = progress;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.deletedAt = deletedAt;
    }

    public Goal update(String title, String description, LocalDate targetDate,
                       GoalStatus status, Instant now) {
        return new Goal(id, userId, title, description, targetDate, status,
                progress, createdAt, now, deletedAt);
    }

    public Goal updateProgress(int progress, Instant now) {
        GoalStatus newStatus = progress == 100 ? GoalStatus.COMPLETED : this.status;
        return new Goal(id, userId, title, description, targetDate, newStatus,
                progress, createdAt, now, deletedAt);
    }

    public Goal softDelete(Instant now) {
        return new Goal(id, userId, title, description, targetDate, status,
                progress, createdAt, now, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return status == GoalStatus.ACTIVE && !isDeleted();
    }

    public GoalId getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
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
