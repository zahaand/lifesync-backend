package ru.zahaand.lifesync.domain.habit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitRepository {

    Habit save(Habit habit);

    Optional<Habit> findByIdAndUserId(HabitId id, UUID userId);

    HabitPage findAllByUserId(UUID userId, String status, int page, int size);

    Habit update(Habit habit);

    record HabitPage(List<Habit> content, long totalElements, int totalPages, int page, int size) {
    }
}
