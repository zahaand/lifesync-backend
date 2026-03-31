package ru.zahaand.lifesync.application.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.DuplicateHabitLogException;
import ru.zahaand.lifesync.domain.habit.exception.HabitInactiveException;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public class CompleteHabitUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteHabitUseCase.class);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CompleteHabitUseCase(HabitRepository habitRepository,
                                HabitLogRepository habitLogRepository,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.eventPublisher = eventPublisher;
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

        HabitCompletedEvent event = new HabitCompletedEvent(
                UUID.randomUUID().toString(),
                habitId.value(),
                userId,
                logDate,
                logId.value(),
                now
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish HabitCompletedEvent: habitId={}, userId={}, error={}",
                    habitId.value(), userId, e.getMessage(), e);
        }

        log.info("Habit completed: habitId={}, logId={}, date={}", habitId.value(), logId.value(), logDate);
        return saved;
    }
}
