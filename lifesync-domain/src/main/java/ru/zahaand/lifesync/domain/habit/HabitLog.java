package ru.zahaand.lifesync.domain.habit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class HabitLog {

    private final HabitLogId id;
    private final HabitId habitId;
    private final UUID userId;
    private final LocalDate logDate;
    private final String note;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public HabitLog(HabitLogId id, HabitId habitId, UUID userId, LocalDate logDate,
                    String note, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.habitId = Objects.requireNonNull(habitId, "habitId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.logDate = Objects.requireNonNull(logDate, "logDate must not be null");
        this.note = note;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.deletedAt = deletedAt;
    }

    public HabitLog softDelete(Instant now) {
        return new HabitLog(id, habitId, userId, logDate, note, createdAt, now, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public HabitLogId getId() {
        return id;
    }

    public HabitId getHabitId() {
        return habitId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public String getNote() {
        return note;
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
