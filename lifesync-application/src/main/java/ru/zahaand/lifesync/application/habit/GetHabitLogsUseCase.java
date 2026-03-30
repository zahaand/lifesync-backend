package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.util.UUID;

public class GetHabitLogsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitLogsUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public GetHabitLogsUseCase(HabitRepository habitRepository,
                                HabitLogRepository habitLogRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
    }

    public HabitLogRepository.HabitLogPage execute(HabitId habitId, UUID userId, int page, int size) {
        log.debug("Getting habit logs: habitId={}, userId={}, page={}, size={}", habitId.value(), userId, page, size);

        habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        return habitLogRepository.findByHabitIdAndUserId(habitId, userId, page, size);
    }
}
