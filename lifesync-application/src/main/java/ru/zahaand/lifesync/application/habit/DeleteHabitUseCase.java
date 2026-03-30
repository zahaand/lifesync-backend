package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class DeleteHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final Clock clock;

    public DeleteHabitUseCase(HabitRepository habitRepository, Clock clock) {
        this.habitRepository = habitRepository;
        this.clock = clock;
    }

    public void execute(HabitId habitId, UUID userId) {
        log.debug("Deleting habit id={}, userId={}", habitId.value(), userId);

        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        Habit deleted = habit.softDelete(Instant.now(clock));
        habitRepository.update(deleted);

        log.info("Habit soft-deleted: id={}, userId={}", habitId.value(), userId);
    }
}
