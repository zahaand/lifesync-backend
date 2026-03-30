package ru.zahaand.lifesync.domain.habit;

import java.util.Objects;
import java.util.UUID;

public record HabitId(UUID value) {

    public HabitId {
        Objects.requireNonNull(value, "HabitId value must not be null");
    }
}
