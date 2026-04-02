package ru.zahaand.lifesync.domain.habit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface HabitStreakRepository {

    Optional<HabitStreak> findByHabitIdAndUserId(HabitId habitId, UUID userId);

    Map<HabitId, HabitStreak> findByHabitIdsAndUserId(List<HabitId> habitIds, UUID userId);

    HabitStreak save(HabitStreak streak);

    HabitStreak update(HabitStreak streak);
}
