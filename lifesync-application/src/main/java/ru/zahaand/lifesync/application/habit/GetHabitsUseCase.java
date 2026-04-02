package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GetHabitsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitsUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final Clock clock;

    public GetHabitsUseCase(HabitRepository habitRepository,
                            HabitLogRepository habitLogRepository,
                            HabitStreakRepository habitStreakRepository,
                            Clock clock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.habitStreakRepository = habitStreakRepository;
        this.clock = clock;
    }

    public EnrichedHabitPage execute(UUID userId, String status, int page, int size) {
        log.debug("Listing habits for userId={}, status={}, page={}, size={}", userId, status, page, size);

        HabitRepository.HabitPage habitPage = habitRepository.findAllByUserId(userId, status, page, size);
        List<Habit> habits = habitPage.content();

        if (habits.isEmpty()) {
            return new EnrichedHabitPage(List.of(), habitPage.totalElements(),
                    habitPage.totalPages(), habitPage.page(), habitPage.size());
        }

        List<HabitId> habitIds = habits.stream().map(Habit::getId).toList();
        LocalDate today = LocalDate.now(clock);

        Map<HabitId, HabitLog> todayLogs = habitLogRepository.findTodayLogsByHabitIds(habitIds, today);
        Map<HabitId, HabitStreak> streaks = habitStreakRepository.findByHabitIdsAndUserId(habitIds, userId);

        List<EnrichedHabit> enriched = habits.stream()
                .map(habit -> toEnrichedHabit(habit, todayLogs, streaks))
                .toList();

        return new EnrichedHabitPage(enriched, habitPage.totalElements(),
                habitPage.totalPages(), habitPage.page(), habitPage.size());
    }

    private EnrichedHabit toEnrichedHabit(Habit habit,
                                          Map<HabitId, HabitLog> todayLogs,
                                          Map<HabitId, HabitStreak> streaks) {
        HabitLog todayLog = todayLogs.get(habit.getId());
        boolean completedToday = todayLog != null;
        HabitLogId todayLogId = completedToday ? todayLog.getId() : null;

        HabitStreak streak = streaks.get(habit.getId());
        int currentStreak = streak != null ? streak.currentStreak() : 0;

        return new EnrichedHabit(habit, completedToday, todayLogId, currentStreak);
    }

    public record EnrichedHabitPage(List<EnrichedHabit> content, long totalElements,
                                    int totalPages, int page, int size) {
    }
}
