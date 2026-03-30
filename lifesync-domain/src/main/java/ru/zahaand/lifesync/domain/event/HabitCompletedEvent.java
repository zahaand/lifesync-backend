package ru.zahaand.lifesync.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HabitCompletedEvent(
        String eventId,
        UUID habitId,
        UUID userId,
        LocalDate logDate,
        UUID completionId,
        Instant occurredAt
) implements DomainEvent {
}
