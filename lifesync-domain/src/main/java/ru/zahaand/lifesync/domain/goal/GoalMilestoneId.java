package ru.zahaand.lifesync.domain.goal;

import java.util.Objects;
import java.util.UUID;

public record GoalMilestoneId(UUID value) {

    public GoalMilestoneId {
        Objects.requireNonNull(value, "GoalMilestoneId value must not be null");
    }
}
