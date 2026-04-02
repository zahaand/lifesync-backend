package ru.zahaand.lifesync.application.habit;

import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitLogId;

import java.util.Objects;

public record EnrichedHabit(Habit habit, boolean completedToday, HabitLogId todayLogId, int currentStreak) {

    public EnrichedHabit {
        Objects.requireNonNull(habit, "habit must not be null");
    }
}
