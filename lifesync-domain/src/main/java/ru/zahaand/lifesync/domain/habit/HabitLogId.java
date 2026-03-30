package ru.zahaand.lifesync.domain.habit;

import java.util.Objects;
import java.util.UUID;

public record HabitLogId(UUID value) {

    public HabitLogId {
        Objects.requireNonNull(value, "HabitLogId value must not be null");
    }
}
