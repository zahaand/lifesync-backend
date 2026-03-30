package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.util.UUID;

public class GetHabitStreakUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitStreakUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitStreakRepository habitStreakRepository;

    public GetHabitStreakUseCase(HabitRepository habitRepository,
                                 HabitStreakRepository habitStreakRepository) {
        this.habitRepository = habitRepository;
        this.habitStreakRepository = habitStreakRepository;
    }

    public HabitStreak execute(HabitId habitId, UUID userId) {
        log.debug("Getting habit streak: habitId={}, userId={}", habitId.value(), userId);

        habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        return habitStreakRepository.findByHabitIdAndUserId(habitId, userId)
                .orElse(new HabitStreak(habitId, 0, 0, null));
    }
}
