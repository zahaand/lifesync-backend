package ru.zahaand.lifesync.domain.goal;

import java.util.Objects;
import java.util.UUID;

public record GoalId(UUID value) {

    public GoalId {
        Objects.requireNonNull(value, "GoalId value must not be null");
    }
}
