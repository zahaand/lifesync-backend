package ru.zahaand.lifesync.domain.event;

import java.time.Instant;
import java.util.UUID;

public record GoalProgressUpdatedEvent(
        String eventId,
        UUID goalId,
        UUID userId,
        UUID habitId,
        int progressPercentage,
        Instant occurredAt
) implements DomainEvent {
}
