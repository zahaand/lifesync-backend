package ru.zahaand.lifesync.domain.habit;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public final class Habit {

    private final HabitId id;
    private final UUID userId;
    private final String title;
    private final String description;
    private final Frequency frequency;
    private final DayOfWeekSet targetDaysOfWeek;
    private final LocalTime reminderTime;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public Habit(HabitId id, UUID userId, String title, String description,
                 Frequency frequency, DayOfWeekSet targetDaysOfWeek, LocalTime reminderTime,
                 boolean active, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        this.frequency = Objects.requireNonNull(frequency, "frequency must not be null");
        this.targetDaysOfWeek = targetDaysOfWeek;
        this.reminderTime = reminderTime;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.deletedAt = deletedAt;
    }

    public Habit update(String title, String description, Frequency frequency,
                        DayOfWeekSet targetDaysOfWeek, LocalTime reminderTime,
                        boolean active, Instant now) {
        return new Habit(id, userId, title, description, frequency, targetDaysOfWeek,
                reminderTime, active, createdAt, now, deletedAt);
    }

    public Habit softDelete(Instant now) {
        return new Habit(id, userId, title, description, frequency, targetDaysOfWeek,
                reminderTime, active, createdAt, now, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return active && !isDeleted();
    }

    public HabitId getId() {
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

    public Frequency getFrequency() {
        return frequency;
    }

    public DayOfWeekSet getTargetDaysOfWeek() {
        return targetDaysOfWeek;
    }

    public LocalTime getReminderTime() {
        return reminderTime;
    }

    public boolean getActive() {
        return active;
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
