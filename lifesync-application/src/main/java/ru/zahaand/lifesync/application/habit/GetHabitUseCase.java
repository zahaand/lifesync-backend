package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GetHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final Clock clock;

    public GetHabitUseCase(HabitRepository habitRepository,
                           HabitLogRepository habitLogRepository,
                           HabitStreakRepository habitStreakRepository,
                           Clock clock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.habitStreakRepository = habitStreakRepository;
        this.clock = clock;
    }

    public EnrichedHabit execute(HabitId habitId, UUID userId) {
        log.debug("Getting habit id={}, userId={}", habitId.value(), userId);

        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        LocalDate today = LocalDate.now(clock);
        Map<HabitId, HabitLog> todayLogs = habitLogRepository.findTodayLogsByHabitIds(List.of(habitId), today);
        HabitLog todayLog = todayLogs.get(habitId);
        boolean completedToday = todayLog != null;
        HabitLogId todayLogId = completedToday ? todayLog.getId() : null;

        HabitStreak streak = habitStreakRepository.findByHabitIdAndUserId(habitId, userId).orElse(null);
        int currentStreak = streak != null ? streak.currentStreak() : 0;

        return new EnrichedHabit(habit, completedToday, todayLogId, currentStreak);
    }
}
