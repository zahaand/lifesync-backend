package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public class CreateHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final Clock clock;

    public CreateHabitUseCase(HabitRepository habitRepository,
                              HabitStreakRepository habitStreakRepository,
                              Clock clock) {
        this.habitRepository = habitRepository;
        this.habitStreakRepository = habitStreakRepository;
        this.clock = clock;
    }

    @Transactional
    public Habit execute(UUID userId, String title, String description,
                         Frequency frequency, DayOfWeekSet targetDaysOfWeek,
                         LocalTime reminderTime) {
        log.debug("Creating habit for userId={}, title={}, frequency={}", userId, title, frequency);

        if (frequency == Frequency.CUSTOM && targetDaysOfWeek == null) {
            throw new IllegalArgumentException("CUSTOM frequency requires targetDaysOfWeek");
        }

        Instant now = Instant.now(clock);
        HabitId habitId = new HabitId(UUID.randomUUID());

        Habit habit = new Habit(habitId, userId, title, description, frequency,
                targetDaysOfWeek, reminderTime, true, now, now, null);

        Habit saved = habitRepository.save(habit);

        HabitStreak streak = new HabitStreak(habitId, 0, 0, null);
        habitStreakRepository.save(streak);

        log.info("Habit created: id={}, userId={}", habitId.value(), userId);
        return saved;
    }
}
