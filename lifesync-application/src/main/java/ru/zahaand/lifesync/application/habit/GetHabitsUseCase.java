package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.habit.HabitRepository;

import java.util.UUID;

public class GetHabitsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetHabitsUseCase.class);

    private final HabitRepository habitRepository;

    public GetHabitsUseCase(HabitRepository habitRepository) {
        this.habitRepository = habitRepository;
    }

    public HabitRepository.HabitPage execute(UUID userId, String status, int page, int size) {
        log.debug("Listing habits for userId={}, status={}, page={}, size={}", userId, status, page, size);
        return habitRepository.findAllByUserId(userId, status, page, size);
    }
}
