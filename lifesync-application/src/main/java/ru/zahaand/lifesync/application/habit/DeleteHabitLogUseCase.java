package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLog;
import ru.zahaand.lifesync.domain.habit.HabitLogId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DeleteHabitLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteHabitLogUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final StreakCalculatorService streakCalculatorService;
    private final Clock clock;

    public DeleteHabitLogUseCase(HabitRepository habitRepository,
                                  HabitLogRepository habitLogRepository,
                                  HabitStreakRepository habitStreakRepository,
                                  StreakCalculatorService streakCalculatorService,
                                  Clock clock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.habitStreakRepository = habitStreakRepository;
        this.streakCalculatorService = streakCalculatorService;
        this.clock = clock;
    }

    @Transactional
    public void execute(HabitId habitId, HabitLogId logId, UUID userId) {
        log.debug("Deleting habit log: habitId={}, logId={}, userId={}", habitId.value(), logId.value(), userId);

        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        HabitLog habitLog = habitLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit log not found: " + logId.value()));

        Instant now = Instant.now(clock);
        HabitLog deleted = habitLog.softDelete(now);
        habitLogRepository.update(deleted);

        recalculateStreak(habitId, userId, habit);

        log.info("Habit log deleted: logId={}, habitId={}", logId.value(), habitId.value());
    }

    private void recalculateStreak(HabitId habitId, UUID userId, Habit habit) {
        List<LocalDate> logDates = habitLogRepository.findLogDatesDesc(habitId, userId);
        HabitStreak newStreak = streakCalculatorService.calculate(
                habitId, habit.getFrequency(), habit.getTargetDaysOfWeek(), logDates);

        Optional<HabitStreak> existing = habitStreakRepository.findByHabitIdAndUserId(habitId, userId);
        if (existing.isPresent()) {
            habitStreakRepository.update(newStreak);
        } else {
            habitStreakRepository.save(newStreak);
        }
    }
}
