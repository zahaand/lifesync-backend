package ru.zahaand.lifesync.domain.notification;

import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.LocalDate;
import java.util.UUID;

public interface SentReminderRepository {

    boolean existsByHabitIdAndDate(HabitId habitId, LocalDate sentDate);

    void save(HabitId habitId, UUID userId, LocalDate sentDate);
}
