package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.util.UUID;

public class GetHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitUseCase.class);

    private final HabitRepository habitRepository;

    public GetHabitUseCase(HabitRepository habitRepository) {
        this.habitRepository = habitRepository;
    }

    public Habit execute(HabitId habitId, UUID userId) {
        log.debug("Getting habit id={}, userId={}", habitId.value(), userId);
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));
    }
}
