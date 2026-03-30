package ru.zahaand.lifesync.domain.habit;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record DayOfWeekSet(Set<DayOfWeek> days) {

    public DayOfWeekSet {
        Objects.requireNonNull(days, "days must not be null");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("DayOfWeekSet must contain at least one day");
        }
        days = Collections.unmodifiableSet(EnumSet.copyOf(days));
    }

    public boolean contains(DayOfWeek day) {
        return days.contains(day);
    }
}
