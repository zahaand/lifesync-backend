package ru.zahaand.lifesync.domain.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitLogRepository {

    HabitLog save(HabitLog habitLog);

    Optional<HabitLog> findByIdAndUserId(HabitLogId id, UUID userId);

    HabitLogPage findByHabitIdAndUserId(HabitId habitId, UUID userId, int page, int size);

    Optional<HabitLog> findByHabitIdAndLogDateAndUserId(HabitId habitId, LocalDate logDate, UUID userId);

    HabitLog update(HabitLog habitLog);

    List<LocalDate> findLogDatesDesc(HabitId habitId, UUID userId);

    record HabitLogPage(List<HabitLog> content, long totalElements, int totalPages, int page, int size) {
    }
}
