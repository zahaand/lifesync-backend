package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class DeleteHabitLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteHabitLogUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DeleteHabitLogUseCase(HabitRepository habitRepository,
                                  HabitLogRepository habitLogRepository,
                                  ApplicationEventPublisher eventPublisher,
                                  Clock clock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.eventPublisher = eventPublisher;
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

        HabitCompletedEvent event = new HabitCompletedEvent(
                UUID.randomUUID().toString(),
                habitId.value(),
                userId,
                habitLog.getLogDate(),
                logId.value(),
                now
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish HabitCompletedEvent: habitId={}, userId={}, error={}",
                    habitId.value(), userId, e.getMessage(), e);
        }

        log.info("Habit log deleted: logId={}, habitId={}", logId.value(), habitId.value());
    }
}
