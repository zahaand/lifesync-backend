package ru.zahaand.lifesync.domain.event;

import java.time.Instant;

public sealed interface DomainEvent
        permits HabitCompletedEvent, GoalProgressUpdatedEvent {

    String eventId();

    Instant occurredAt();
}
