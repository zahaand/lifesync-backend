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
import ru.zahaand.lifesync.domain.habit.exception.DuplicateHabitLogException;
import ru.zahaand.lifesync.domain.habit.exception.HabitInactiveException;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CompleteHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final StreakCalculatorService streakCalculatorService;
    private final Clock clock;

    public CompleteHabitUseCase(HabitRepository habitRepository,
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
    public HabitLog execute(HabitId habitId, UUID userId, LocalDate logDate, String note) {
        log.debug("Completing habit id={}, userId={}, date={}", habitId.value(), userId, logDate);

        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        if (!habit.isActive()) {
            throw new HabitInactiveException("Habit is inactive: " + habitId.value());
        }

        Optional<HabitLog> existing = habitLogRepository.findByHabitIdAndLogDateAndUserId(habitId, logDate, userId);
        if (existing.isPresent()) {
            throw new DuplicateHabitLogException(
                    "Habit already completed on " + logDate + ": " + habitId.value());
        }

        Instant now = Instant.now(clock);
        HabitLogId logId = new HabitLogId(UUID.randomUUID());
        HabitLog habitLog = new HabitLog(logId, habitId, userId, logDate, note, now, now, null);
        HabitLog saved = habitLogRepository.save(habitLog);

        recalculateStreak(habitId, userId, habit);

        log.info("Habit completed: habitId={}, logId={}, date={}", habitId.value(), logId.value(), logDate);
        return saved;
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
