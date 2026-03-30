package ru.zahaand.lifesync.domain.habit;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record HabitStreak(HabitId habitId, int currentStreak, int longestStreak, LocalDate lastLogDate) {

    public HabitStreak {
        Objects.requireNonNull(habitId, "habitId must not be null");
        if (currentStreak < 0) {
            throw new IllegalArgumentException("currentStreak must not be negative");
        }
        if (longestStreak < 0) {
            throw new IllegalArgumentException("longestStreak must not be negative");
        }
    }

    public Optional<LocalDate> getLastLogDate() {
        return Optional.ofNullable(lastLogDate);
    }
}
