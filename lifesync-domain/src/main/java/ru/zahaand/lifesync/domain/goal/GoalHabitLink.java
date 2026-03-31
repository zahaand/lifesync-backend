package ru.zahaand.lifesync.domain.goal;

import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.Instant;
import java.util.Objects;

public final class GoalHabitLink {

    private final GoalHabitLinkId id;
    private final GoalId goalId;
    private final HabitId habitId;
    private final Instant createdAt;
    private final Instant updatedAt;

    public GoalHabitLink(GoalHabitLinkId id, GoalId goalId, HabitId habitId,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.goalId = Objects.requireNonNull(goalId, "goalId must not be null");
        this.habitId = Objects.requireNonNull(habitId, "habitId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public GoalHabitLinkId getId() {
        return id;
    }

    public GoalId getGoalId() {
        return goalId;
    }

    public HabitId getHabitId() {
        return habitId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
