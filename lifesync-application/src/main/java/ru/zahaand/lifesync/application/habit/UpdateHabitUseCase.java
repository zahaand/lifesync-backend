package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UpdateHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final StreakCalculatorService streakCalculatorService;
    private final Clock clock;

    public UpdateHabitUseCase(HabitRepository habitRepository,
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
    public Habit execute(HabitId habitId, UUID userId, UpdateCommand command) {
        log.debug("Updating habit id={}, userId={}", habitId.value(), userId);

        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        String newTitle = command.title() != null ? command.title() : habit.getTitle();
        String newDescription = command.descriptionProvided() ? command.description() : habit.getDescription();
        Frequency newFrequency = command.frequency() != null ? command.frequency() : habit.getFrequency();
        DayOfWeekSet newTargetDays = command.targetDaysProvided() ? command.targetDaysOfWeek() : habit.getTargetDaysOfWeek();
        LocalTime newReminderTime = command.reminderTimeProvided() ? command.reminderTime() : habit.getReminderTime();
        boolean newActive = command.isActive() != null ? command.isActive() : habit.getActive();

        if (newFrequency == Frequency.CUSTOM && newTargetDays == null) {
            throw new IllegalArgumentException("CUSTOM frequency requires targetDaysOfWeek");
        }

        Instant now = Instant.now(clock);
        Habit updated = habit.update(newTitle, newDescription, newFrequency, newTargetDays,
                newReminderTime, newActive, now);
        Habit saved = habitRepository.update(updated);

        boolean frequencyChanged = habit.getFrequency() != newFrequency;
        if (frequencyChanged) {
            recalculateStreak(habitId, userId, newFrequency, newTargetDays);
        }

        log.info("Habit updated: id={}, userId={}", habitId.value(), userId);
        return saved;
    }

    private void recalculateStreak(HabitId habitId, UUID userId, Frequency frequency, DayOfWeekSet targetDays) {
        List<LocalDate> logDates = habitLogRepository.findLogDatesDesc(habitId, userId);
        HabitStreak newStreak = streakCalculatorService.calculate(habitId, frequency, targetDays, logDates);

        Optional<HabitStreak> existing = habitStreakRepository.findByHabitIdAndUserId(habitId, userId);
        if (existing.isPresent()) {
            habitStreakRepository.update(newStreak);
        } else {
            habitStreakRepository.save(newStreak);
        }
    }

    public record UpdateCommand(
            String title,
            String description,
            boolean descriptionProvided,
            Frequency frequency,
            DayOfWeekSet targetDaysOfWeek,
            boolean targetDaysProvided,
            LocalTime reminderTime,
            boolean reminderTimeProvided,
            Boolean isActive
    ) {
    }
}
