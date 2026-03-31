package ru.zahaand.lifesync.domain.goal;

import java.util.Objects;
import java.util.UUID;

public record GoalHabitLinkId(UUID value) {

    public GoalHabitLinkId {
        Objects.requireNonNull(value, "GoalHabitLinkId value must not be null");
    }
}
