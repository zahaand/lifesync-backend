# Feature Specification: Kafka Event-Driven Architecture

**Feature Branch**: `005-kafka-events`
**Created**: 2026-03-30
**Status**: Draft
**Input**: User description: "Sprint 5 — Kafka Event-Driven Architecture for LifeSync Backend"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Habit Completion Triggers Asynchronous Events (Priority: P1)

When a user completes a habit, the system publishes a domain event to a message broker. Three independent consumers process this event asynchronously: one recalculates the habit streak, one updates analytics data, and one checks whether a notification should be sent. This decouples the completion action from its downstream effects, ensuring the user receives an immediate response while background processing happens reliably.

**Why this priority**: This is the foundational event-driven pattern. Without it, no downstream consumer can function. It also moves streak recalculation from synchronous (current Sprint 4 behavior) to asynchronous, improving response time for habit completion.

**Independent Test**: Can be fully tested by completing a habit and verifying that the event is published to the broker with the correct payload (habitId, userId, logDate, completionId, timestamp). Consumers can be tested independently by feeding events directly.

**Acceptance Scenarios**:

1. **Given** a user with an active habit, **When** the user completes the habit via POST /api/v1/habits/{id}/complete, **Then** a HabitCompletedEvent is published to the `habit.log.completed` topic with the correct payload within 1 second of the successful response.
2. **Given** a HabitCompletedEvent is published, **When** the streak calculator consumer receives it, **Then** the streak for that habit is recalculated and persisted.
3. **Given** a HabitCompletedEvent is published, **When** the analytics consumer receives it, **Then** the analytics cache for that user is invalidated and an informational log entry is written.
4. **Given** the same event is delivered twice (duplicate), **When** any consumer receives the duplicate, **Then** the consumer detects the duplicate via the processed events registry, logs a warning, and skips processing without side effects.

---

### User Story 2 - Streak Milestone Triggers Telegram Notification (Priority: P2)

When a user reaches a streak milestone (7, 14, 21, 30, 60, or 90 consecutive days), and the user has linked their Telegram account, the system sends a congratulatory notification via Telegram. If the user has not linked Telegram or no milestone was reached, no notification is sent.

**Why this priority**: Notifications are a key engagement feature and demonstrate the full event-driven chain from completion to user-facing output. However, the core event infrastructure (US1) must exist first.

**Independent Test**: Can be tested by feeding a HabitCompletedEvent to the notification consumer with a pre-configured streak at a milestone boundary (e.g., streak reaches exactly 7) and a user with a configured Telegram chat ID. Verify that a notification message is dispatched. Test the negative case (no milestone, no Telegram) to verify silence.

**Acceptance Scenarios**:

1. **Given** a user with telegramChatId configured and a current streak of 6, **When** a HabitCompletedEvent is processed and the new streak becomes 7, **Then** a Telegram notification with a milestone congratulation message is sent to the user.
2. **Given** a user without telegramChatId configured, **When** a HabitCompletedEvent is processed and a milestone is reached, **Then** no notification is sent and no error is logged.
3. **Given** a user with a current streak of 5, **When** a HabitCompletedEvent is processed and the new streak becomes 6, **Then** no notification is sent (6 is not a milestone).

---

### User Story 3 - Failed Events Are Retried and Sent to Dead Letter Queue (Priority: P2)

When an event consumer fails to process a message, the system retries with exponential backoff (1s, 2s, 4s). After 3 failed attempts, the message is sent to a dead letter queue (DLQ) for manual investigation. This ensures no events are silently lost and operators can monitor and recover from failures.

**Why this priority**: Reliability is essential for an event-driven system. Without retry and DLQ, transient failures (network blips, temporary DB unavailability) would cause silent data loss.

**Independent Test**: Can be tested by configuring a consumer to throw an exception on processing, then verifying that after 3 retries with increasing delays, the original message appears in the DLQ topic.

**Acceptance Scenarios**:

1. **Given** a consumer that fails to process a message, **When** the first attempt fails, **Then** the system retries after 1 second.
2. **Given** a consumer that has failed twice, **When** the third attempt also fails, **Then** the message is published to the corresponding DLQ topic and an error is logged with full message context.
3. **Given** a consumer that fails on the first attempt but succeeds on retry, **Then** the message is processed successfully and not sent to the DLQ.

---

### User Story 4 - Goal Progress Event Stub (Priority: P3)

The system defines a GoalProgressUpdatedEvent and its corresponding topic as a stub for Sprint 6. No consumers are implemented, but the event structure and topic are available for future use. A publisher interface is defined so Sprint 6 can implement it without modifying event infrastructure.

**Why this priority**: This is a forward-looking stub that prevents rework in Sprint 6. It has no user-facing value in this sprint but establishes the contract early.

**Independent Test**: Can be tested by verifying the event class exists with the expected fields and the topic configuration is present. No consumer behavior to test.

**Acceptance Scenarios**:

1. **Given** the event infrastructure is deployed, **When** a developer inspects the topic configuration, **Then** the `goal.progress.updated` topic exists with 3 partitions and a corresponding DLQ topic exists.
2. **Given** a GoalProgressUpdatedEvent is defined, **When** a developer inspects the event class, **Then** it contains fields for goalId, userId, habitId, progressPercentage, and occurredAt.

---

### Edge Cases

- What happens when the message broker is temporarily unavailable during event publishing? The producer must handle the failure gracefully — log an error and not block the HTTP response to the user. The habit completion itself (database write) must still succeed.
- What happens when a consumer receives a malformed event (missing required fields)? The consumer must log an error and send the message to the DLQ without retrying (retries would not fix a malformed payload).
- What happens when the processed_events table is unavailable during idempotency check? The consumer must fail and rely on the retry/DLQ mechanism rather than processing without idempotency guarantees.
- What happens when two instances of the same consumer receive the same event due to rebalancing? The idempotency check via processed_events ensures only one instance processes the event; the second sees it as already processed and skips.
- What happens when the Telegram API is unreachable? The notification consumer must fail, triggering retry/DLQ. It must not swallow the error silently.

## Clarifications

### Session 2026-03-30

- Q: Does the async StreakCalculatorConsumer replace or coexist with Sprint 4's synchronous streak calculation in CompleteHabitUseCase? → A: Async replaces sync — remove streak call from CompleteHabitUseCase.
- Q: Telegram integration scope — real API client or log-only stub? → A: Real adapter with config flag — full TelegramBots implementation, toggleable via environment variable. Log-only when disabled.
- Q: Kafka partition key for habit.log.completed topic? → A: habitId — all events for the same habit go to the same partition, guaranteeing ordering per habit.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST publish a HabitCompletedEvent to the `habit.log.completed` topic when a habit completion succeeds via the existing POST endpoint. The partition key MUST be `habitId` to guarantee ordering of events per habit.
- **FR-002**: The HabitCompletedEvent MUST contain: habitId, userId, logDate, completionId, and occurredAt timestamp.
- **FR-003**: The event producer MUST NOT block the HTTP response — publishing failures MUST be logged but MUST NOT cause the completion endpoint to return an error.
- **FR-004**: System MUST provide a streak calculator consumer that recalculates the habit streak upon receiving a HabitCompletedEvent. The synchronous StreakCalculatorService call MUST be removed from CompleteHabitUseCase — streak calculation is now exclusively event-driven.
- **FR-005**: System MUST provide an analytics updater consumer that invalidates analytics cache upon receiving a HabitCompletedEvent (placeholder behavior — full analytics in Sprint 7).
- **FR-006**: System MUST provide a Telegram notification consumer that sends milestone notifications (at streaks of 7, 14, 21, 30, 60, 90 days) to users with a configured Telegram chat ID. The implementation MUST use TelegramBots library with a real adapter, guarded by a configuration flag (`lifesync.telegram.enabled`). When disabled, messages are logged at INFO level but not sent.
- **FR-007**: The notification consumer MUST skip silently (no error, no notification) when the user has no Telegram chat ID configured or when no milestone is reached.
- **FR-008**: All consumers MUST check the processed_events table before processing and skip duplicates with a WARN log.
- **FR-009**: All consumers MUST record successfully processed events in the processed_events table after processing.
- **FR-010**: Each topic MUST have a corresponding DLQ topic (suffix `.dlq`).
- **FR-011**: Failed messages MUST be retried 3 times with exponential backoff (1s, 2s, 4s) before being sent to the DLQ.
- **FR-012**: System MUST log ERROR with full event context when a message is sent to the DLQ.
- **FR-013**: System MUST define a GoalProgressUpdatedEvent with fields: goalId, userId, habitId, progressPercentage, occurredAt — as a stub for Sprint 6.
- **FR-014**: System MUST configure the `goal.progress.updated` topic and its DLQ topic, but MUST NOT implement any consumers for it in this sprint.
- **FR-015**: Malformed events (missing required fields) MUST be sent directly to the DLQ without retry.
- **FR-016**: Each consumer MUST log at DEBUG level: topic, partition, offset before processing each event.
- **FR-017**: Each consumer MUST log at INFO level upon successful processing with relevant business context (habitId, userId, result).

### Key Entities

- **HabitCompletedEvent**: Domain event representing a successful habit completion. Contains habitId, userId, logDate, completionId, occurredAt. Published to `habit.log.completed` topic.
- **GoalProgressUpdatedEvent**: Domain event stub for goal progress changes. Contains goalId, userId, habitId, progressPercentage, occurredAt. Published to `goal.progress.updated` topic. No consumers in this sprint.
- **ProcessedEvent**: Registry entry for idempotency. Contains eventId, consumerGroup, processedAt. Used by all consumers to detect and skip duplicates.
- **Streak Milestone**: A business concept (not a persisted entity) representing notable streak values: 7, 14, 21, 30, 60, 90 days.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Habit completion response time remains under 500ms after introducing event publishing (no degradation from pre-event baseline).
- **SC-002**: 100% of habit completions result in a corresponding event being published to the broker (verified via integration tests).
- **SC-003**: Duplicate events are detected and skipped in 100% of cases (verified via integration tests with intentional duplicate delivery).
- **SC-004**: Failed messages appear in the DLQ after exactly 3 retry attempts (verified via integration tests with forced consumer failure).
- **SC-005**: Telegram milestone notifications are sent for all 6 milestone values (7, 14, 21, 30, 60, 90) when the user has Telegram configured (verified via unit tests).
- **SC-006**: No notifications are sent when the user lacks Telegram configuration or when no milestone is reached (verified via unit tests).
- **SC-007**: All consumers achieve ≥ 80% unit test coverage on their business logic.

## Assumptions

- The existing `processed_events` table (created in Sprint 1 migrations) is available and has the schema needed for idempotency checks.
- The `docker-compose.yml` already includes a Kafka broker (configured in Sprint 1 infrastructure).
- The `telegramChatId` field exists on the user profile (added in Sprint 2) and is queryable via the existing UserRepository.
- The asynchronous StreakCalculatorConsumer replaces Sprint 4's synchronous streak calculation. The direct StreakCalculatorService call MUST be removed from CompleteHabitUseCase — streak is calculated only by the Kafka consumer.
- Telegram Bot token and configuration are provided via environment variables (per Constitution §VI). A real TelegramBots adapter is implemented, guarded by `lifesync.telegram.enabled` (default: `false`). When disabled, the adapter logs the message at INFO level instead of sending. No stub/mock — the adapter is fully functional when enabled.
- The analytics consumer is intentionally a placeholder — it logs and invalidates a cache entry but does not compute analytics. Full analytics is Sprint 7 scope.
- Consumer group IDs follow the pattern: `lifesync-{consumer-name}` (e.g., `lifesync-streak-calculator`).
- Event serialization uses JSON format for human readability and debugging ease.
