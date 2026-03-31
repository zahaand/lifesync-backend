# Implementation Plan: Kafka Event-Driven Architecture

**Branch**: `005-kafka-events` | **Date**: 2026-03-30 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-kafka-events/spec.md`

## Summary

Migrate habit completion and log deletion from synchronous streak recalculation to an event-driven architecture using Spring Kafka. Publish `HabitCompletedEvent` from use cases; process asynchronously via three independent consumers (streak calculator, analytics updater, Telegram notifier). Add DLQ with exponential backoff retry, idempotency via `processed_events` table, and a `GoalProgressUpdatedEvent` stub for Sprint 6. Implement a real Telegram adapter guarded by a config flag.

## Technical Context

**Language/Version**: Java 21 LTS
**Primary Dependencies**: Spring Boot 3.5.x, Spring Kafka 3.x, jOOQ 3.19, Liquibase 4.x, TelegramBots library
**Storage**: PostgreSQL 16 (via Docker Compose), Apache Kafka (Confluent 7.7.1 via Docker Compose)
**Testing**: JUnit 5 + AssertJ + Mockito + Testcontainers (PostgreSQL + Kafka)
**Target Platform**: Linux server (Docker)
**Project Type**: web-service (REST API)
**Performance Goals**: Habit completion response < 500ms (non-blocking event publishing), streak recalculation via consumer < 1s
**Constraints**: No Lombok, constructor injection only, all fields final, no null from public methods
**Scale/Scope**: 3 consumers, 2 topics + 2 DLQ topics, 1 Telegram adapter, idempotent processing

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | Domain events + ports in domain. Kafka publisher + consumers + Telegram adapter in infrastructure. Use cases in application. No layer violations. |
| II | API First | PASS | No new API endpoints. Existing endpoints unchanged. No YAML modifications needed. |
| III | User Data Isolation | PASS | Consumers receive userId in event payload. All queries include userId predicate. |
| IV | Single Responsibility | PASS | One consumer per concern (streak, analytics, notification). UseCase publishes event, does not process it. Telegram adapter in infrastructure, not in consumer. |
| V | Liquibase Migrations | PASS | `processed_events` table already exists (V11). `notification_logs` already exists (V10). No new migrations needed — existing `processed_events` schema (id, event_id, event_type, processed_at) is sufficient. Need to add `consumer_group` column (V17). |
| VI | Secrets via Environment Variables | PASS | Telegram bot token via env var `TELEGRAM_BOT_TOKEN`. Kafka bootstrap via env var. |
| VII | Portfolio Readability | PASS | No Lombok. No speculative features. All identifiers English. |
| VIII | Logging Standards | PASS | Consumers: DEBUG (topic, partition, offset), INFO (business success), WARN (duplicate/skip), ERROR (DLQ). |
| IX | Code Style | PASS | All fields final, constructor injection, curly braces always, explicit constructors. |
| X | Testing Standards | PASS | Unit tests with Mockito for consumers. ITs with Testcontainers Kafka + PG. Clock.fixed() for streak. JaCoCo ≥ 80%. |
| XI | Code/Doc Language | PASS | English code/identifiers, Russian commit bodies, Conventional Commits. |
| XII | OpenAPI Documentation Standards | N/A | No API changes in this sprint. |

All gates pass. One migration needed (V17 — add `consumer_group` to `processed_events`).

## Project Structure

### Documentation (this feature)

```text
specs/005-kafka-events/
├── plan.md              # This file
├── spec.md              # Feature specification
├── checklists/          # Specification quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
lifesync-domain/
└── src/main/java/ru/zahaand/lifesync/domain/
    ├── event/
    │   ├── DomainEvent.java                    # Sealed interface: eventId, occurredAt
    │   ├── HabitCompletedEvent.java             # Record: habitId, userId, logDate, completionId, occurredAt
    │   └── GoalProgressUpdatedEvent.java        # Record stub: goalId, userId, habitId, progressPercentage, occurredAt
    └── user/
        └── TelegramNotificationSender.java      # Port interface: send(String chatId, String message)

lifesync-application/
└── src/main/java/ru/zahaand/lifesync/application/habit/
    ├── CompleteHabitUseCase.java                 # Modified: remove streak calc, add event publishing
    └── DeleteHabitLogUseCase.java                # Modified: remove streak calc, add event publishing

lifesync-infrastructure/
└── src/main/java/ru/zahaand/lifesync/infrastructure/
    ├── event/
    │   ├── KafkaHabitEventPublisher.java         # @Component: @TransactionalEventListener → KafkaTemplate
    │   ├── ProcessedEventRepository.java         # jOOQ repository for idempotency checks
    │   └── config/
    │       └── KafkaTopicConfig.java             # Topic beans: habit.log.completed, goal.progress.updated, DLQs
    ├── habit/
    │   ├── StreakCalculatorConsumer.java          # @KafkaListener: recalculates streak
    │   └── AnalyticsUpdaterConsumer.java          # @KafkaListener: placeholder (log + cache invalidation)
    └── notification/
        ├── TelegramNotificationConsumer.java      # @KafkaListener: milestone check + send
        └── TelegramNotificationAdapter.java       # Implements TelegramNotificationSender (real API or log)
└── src/main/resources/db/changelog/system/
    └── V17__add_consumer_group_to_processed_events.xml  # Add consumer_group column + update unique constraint

lifesync-app/
└── src/main/java/ru/zahaand/lifesync/app/config/
    └── UseCaseConfig.java                        # Updated: wire HabitEventPublisher into use cases
└── src/main/resources/
    └── application.yml                           # Updated: Kafka consumer groups, serialization, telegram config

lifesync-web/
└── src/test/java/ru/zahaand/lifesync/web/
    └── BaseIT.java                               # Updated: add KafkaContainer Testcontainer
```

**Structure Decision**: All Kafka infrastructure lives in `lifesync-infrastructure` per Constitution §I (infrastructure layer handles adapters). Domain events are pure Java records in `lifesync-domain`. No new modules needed — all new code fits existing hexagonal structure.

## Implementation Phases

### Phase 0: Database Migration & Domain Events

**0.1 — Liquibase migration V17: add consumer_group to processed_events**

The existing `processed_events` table has a unique constraint on `event_id` alone. Since multiple consumers process the same event independently, each consumer needs its own idempotency record. Add `consumer_group varchar(100) NOT NULL` column and replace the unique constraint with a composite unique on `(event_id, consumer_group)`.

File: `lifesync-infrastructure/src/main/resources/db/changelog/system/V17__add_consumer_group_to_processed_events.xml`

Changes:
- `<addColumn>` — add `consumer_group varchar(100) NOT NULL DEFAULT 'unknown'`
- `<dropUniqueConstraint>` — remove `uq_processed_events_event_id`
- `<addUniqueConstraint>` — add composite `(event_id, consumer_group)`
- `<createIndex>` — index on `consumer_group` for queries
- `<rollback>` block

Update `db.changelog-master.xml` to include V17.

Regenerate jOOQ classes: `mvn generate-sources -pl lifesync-infrastructure -Pjooq-codegen`

**0.2 — Domain events (lifesync-domain)**

Create `ru.zahaand.lifesync.domain.event` package:

`DomainEvent` — sealed interface permitting `HabitCompletedEvent` and `GoalProgressUpdatedEvent`:
```java
public sealed interface DomainEvent
        permits HabitCompletedEvent, GoalProgressUpdatedEvent {
    String eventId();
    Instant occurredAt();
}
```

`HabitCompletedEvent` — record implementing DomainEvent:
```java
public record HabitCompletedEvent(
        String eventId,
        UUID habitId,
        UUID userId,
        LocalDate logDate,
        UUID completionId,
        Instant occurredAt
) implements DomainEvent { }
```

`GoalProgressUpdatedEvent` — stub record implementing DomainEvent:
```java
public record GoalProgressUpdatedEvent(
        String eventId,
        UUID goalId,
        UUID userId,
        UUID habitId,
        int progressPercentage,
        Instant occurredAt
) implements DomainEvent { }
```

**0.3 — Domain ports (lifesync-domain)**

`TelegramNotificationSender` port in `ru.zahaand.lifesync.domain.user`:
```java
public interface TelegramNotificationSender {
    void send(String chatId, String message);
}
```

NOTE: `HabitEventPublisher` domain port is NOT created. Use cases inject `ApplicationEventPublisher` (Spring) directly — `spring-context` is available in `lifesync-application` via existing Spring Boot dependencies. `KafkaHabitEventPublisher` listens via `@TransactionalEventListener(AFTER_COMMIT)` and does not need a domain port interface.

### Phase 1: Kafka Infrastructure & Configuration

**1.1 — Kafka topic configuration (lifesync-infrastructure)**

Create `KafkaTopicConfig` in `ru.zahaand.lifesync.infrastructure.event.config`:
- `habit.log.completed` — 3 partitions, replication factor 1 (dev)
- `habit.log.completed.dlq` — 1 partition
- `goal.progress.updated` — 3 partitions, replication factor 1
- `goal.progress.updated.dlq` — 1 partition

Use `NewTopic` beans via `TopicBuilder`.

**1.2 — Kafka producer & consumer configuration (lifesync-app)**

Update `application.yml`:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: ru.zahaand.lifesync.domain.event

lifesync:
  telegram:
    enabled: ${TELEGRAM_ENABLED:false}
    bot-token: ${TELEGRAM_BOT_TOKEN:}
    bot-username: ${TELEGRAM_BOT_USERNAME:}
```

Create `KafkaConsumerConfig` in `lifesync-infrastructure` with:
- `ConcurrentKafkaListenerContainerFactory` bean
- `DefaultErrorHandler` with exponential backoff (1s, 2s, 4s, maxAttempts=4 i.e. 1 original + 3 retries)
- `DeadLetterPublishingRecoverer` to route failed messages to `*.dlq` topics
- Separate consumer groups per listener: `lifesync-streak-calculator`, `lifesync-analytics-updater`, `lifesync-telegram-notifier`

**1.3 — Kafka event publisher (lifesync-infrastructure)**

Create `KafkaHabitEventPublisher` (`@Component`):
- Inject `KafkaTemplate<String, HabitCompletedEvent>`
- `publish(HabitCompletedEvent event)`:
  - Partition key: `event.habitId().toString()`
  - Topic: `habit.log.completed`
  - Fire-and-forget with callback logging (not blocking HTTP response per FR-003)
  - On success: log DEBUG
  - On failure: log ERROR (do NOT throw — event publishing failure must not break completion)

**Kafka failure resilience:** If Kafka is unavailable when publishing `HabitCompletedEvent`:
- Log ERROR with habitId, userId, error message
- Do NOT rethrow the exception to the caller
- HTTP 201 is still returned (log entry is saved in DB)
- Streak recalculation will be delayed until Kafka recovers or manual replay

This is an acceptable tradeoff: the habit log is the source of truth, Kafka is async.

**1.4 — ProcessedEventRepository (lifesync-infrastructure)**

Create `ProcessedEventRepository` in `ru.zahaand.lifesync.infrastructure.event`:
- Uses jOOQ DSL with generated `PROCESSED_EVENTS` table
- `boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup)` — idempotency check
- `void save(String eventId, String eventType, String consumerGroup)` — register processed event

### Phase 2: Modify Use Cases (lifesync-application)

**2.1 — Update CompleteHabitUseCase**

Current: `CompleteHabitUseCase(HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock)`

New: `CompleteHabitUseCase(HabitRepository, HabitLogRepository, ApplicationEventPublisher, Clock)`

Changes:
- Remove `HabitStreakRepository` and `StreakCalculatorService` dependencies
- Remove `recalculateStreak()` private method entirely
- Add `ApplicationEventPublisher` (Spring) dependency
- After saving HabitLog, build and publish `HabitCompletedEvent`:
  ```java
  var event = new HabitCompletedEvent(
      UUID.randomUUID().toString(), habitId.value(), userId,
      logDate, saved.getId().value(), Instant.now(clock));
  applicationEventPublisher.publishEvent(event);
  ```
- `@Transactional` remains for the DB write

**Transaction boundary rule:** `HabitCompletedEvent` MUST be published AFTER the database transaction commits. Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` pattern:
1. `CompleteHabitUseCase` saves `HabitLog` inside `@Transactional`
2. Publishes `ApplicationEvent` (Spring internal) via `ApplicationEventPublisher`
3. `KafkaHabitEventPublisher` listens with `@TransactionalEventListener(AFTER_COMMIT)` and forwards to Kafka

This ensures Kafka never receives an event for data that was rolled back.

**2.2 — Update DeleteHabitLogUseCase**

Current: `DeleteHabitLogUseCase(HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock)`

New: `DeleteHabitLogUseCase(HabitRepository, HabitLogRepository, ApplicationEventPublisher, Clock)`

Same pattern: remove streak recalculation, publish `HabitCompletedEvent` via `applicationEventPublisher.publishEvent()` after soft-delete so the streak consumer recalculates. The event semantically means "habit log state changed — recalculate streak." The deleted log's ID is used as `completionId` — consumer logic handles both completion and deletion identically (recalculate from current logs).

**2.3 — Update UseCaseConfig (lifesync-app)**

- Update `completeHabitUseCase` bean: replace `HabitStreakRepository` + `StreakCalculatorService` with `ApplicationEventPublisher`
- Update `deleteHabitLogUseCase` bean: same replacement
- Also update `TestUseCaseConfig.java` — same constructor changes for both use case beans
- `StreakCalculatorService` bean remains (used by `StreakCalculatorConsumer` and `UpdateHabitUseCase`)

### Phase 3: Kafka Consumers (lifesync-infrastructure)

**3.1 — StreakCalculatorConsumer**

Package: `ru.zahaand.lifesync.infrastructure.habit`

Dependencies: `HabitRepository`, `HabitLogRepository`, `HabitStreakRepository`, `StreakCalculatorService`, `ProcessedEventRepository`

```java
@KafkaListener(topics = "habit.log.completed",
               groupId = "lifesync-streak-calculator")
```

Processing flow:
1. Log DEBUG: topic, partition, offset
2. Check `processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "lifesync-streak-calculator")`
   - If exists: log WARN "Duplicate event, skipping" → return
3. Fetch habit from repository (need frequency, targetDaysOfWeek for calculation)
4. Fetch all log dates via `habitLogRepository.findLogDatesDesc()`
5. Call `streakCalculatorService.calculate()`
6. Save/update streak in `habitStreakRepository`
7. Register event in `processedEventRepository.save()`
8. Log INFO: "Streak recalculated for habitId={}, currentStreak={}, longestStreak={}"

**Idempotency DB failure rule (all consumers):** If the `processed_events` DB check throws an exception (e.g. connection failure), the consumer MUST rethrow the exception. This triggers the Kafka retry mechanism. After retry exhaustion, the message goes to DLQ. Silent processing on DB failure is PROHIBITED — it risks duplicate event processing.

**3.2 — AnalyticsUpdaterConsumer**

Package: `ru.zahaand.lifesync.infrastructure.habit`

Dependencies: `ProcessedEventRepository`

```java
@KafkaListener(topics = "habit.log.completed",
               groupId = "lifesync-analytics-updater")
```

Processing flow (placeholder per FR-005):
1. Log DEBUG: topic, partition, offset
2. Idempotency check (same pattern as streak)
3. Log INFO: "Analytics cache invalidated for userId={}, habitId={}"
4. Register event as processed

**3.3 — TelegramNotificationConsumer**

Package: `ru.zahaand.lifesync.infrastructure.notification`

Dependencies: `HabitRepository`, `HabitLogRepository`, `StreakCalculatorService`, `UserRepository`, `TelegramNotificationSender`, `ProcessedEventRepository`

```java
@KafkaListener(topics = "habit.log.completed",
               groupId = "lifesync-telegram-notifier")
```

Milestone thresholds: `Set.of(7, 14, 21, 30, 60, 90)`

**Design decision**: The TelegramNotificationConsumer recalculates the streak independently (injecting `HabitRepository`, `HabitLogRepository`, `StreakCalculatorService`) rather than reading from `habitStreakRepository`. Consumer groups are independent — no ordering guarantee between StreakCalculatorConsumer and this consumer. Independent calculation ensures correctness regardless of consumer ordering. Cost: duplicate calculation, but milestone check is infrequent.

Processing flow:
1. Log DEBUG: topic, partition, offset
2. Idempotency check
3. Recalculate streak independently (fetch habit, log dates, call `streakCalculatorService.calculate()`)
4. Check if `currentStreak` is a milestone value
   - If not: log DEBUG "No milestone reached" → register + return
5. Fetch user from `userRepository.findById()`
6. Check `user.getProfile().telegramChatId()` is not null/blank
   - If null: log DEBUG "User has no Telegram" → register + return
7. Build congratulation message: "You've reached a {N}-day streak! Keep going!"
8. Call `telegramNotificationSender.send(chatId, message)`
9. Register event as processed
10. Log INFO: "Telegram milestone notification sent: userId={}, streak={}"

### Phase 4: Telegram Adapter (lifesync-infrastructure)

**4.1 — TelegramNotificationAdapter**

Package: `ru.zahaand.lifesync.infrastructure.notification`

Implements `TelegramNotificationSender`.

Two modes controlled by `lifesync.telegram.enabled`:

**When enabled=true:**
- Use TelegramBots library `TelegramBotsApi` + `TelegramLongPollingBot` (or `SendMessage` API directly)
- Actually simpler: use `org.telegram.telegrambots.meta.api.methods.send.SendMessage` with `DefaultAbsSender`
- Inject bot token and username from config
- Send message to the specified chatId
- On failure: throw exception (let retry/DLQ handle it)

**When enabled=false:**
- Log INFO: "Telegram disabled, would send to chatId={}: {message}"
- No actual API call

**4.2 — Dependencies**

Add to `lifesync-infrastructure/pom.xml`:
```xml
<dependency>
    <groupId>org.telegram</groupId>
    <artifactId>telegrambots</artifactId>
    <version>6.9.7.1</version>
</dependency>
```

Or use the lightweight `telegrambots-client` if available. Check latest compatible version with Spring Boot 3.5.x.

### Phase 5: Test Infrastructure & BaseIT Update

**5.1 — Update BaseIT with KafkaContainer**

Current BaseIT uses hardcoded `localhost:9092` for Kafka. Replace with Testcontainers:

```java
@Container
static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));
```

In `@DynamicPropertySource`:
```java
registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
```

Add `testcontainers-kafka` dependency to `lifesync-web/pom.xml` (test scope):
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

### Phase 6: Unit Tests

**6.1 — CompleteHabitUseCaseTest (update existing)**

- Remove streak recalculation assertions
- Add: verify `habitEventPublisher.publish()` called with correct event payload
- Add: verify event contains correct habitId, userId, logDate, completionId
- Add: verify event publishing failure does not roll back habit log save

**6.2 — DeleteHabitLogUseCaseTest (update existing)**

- Same pattern: remove streak assertions, add event publishing verification

**6.3 — StreakCalculatorConsumerTest (new)**

- Test happy path: event received → streak recalculated → saved
- Test idempotency: duplicate event → WARN logged, no processing
- Test habit not found: exception → retry/DLQ
- Mock: `ProcessedEventRepository`, `HabitRepository`, `HabitLogRepository`, `HabitStreakRepository`, `StreakCalculatorService`
- Use `@ExtendWith(MockitoExtension.class)`, no Spring context

**6.4 — AnalyticsUpdaterConsumerTest (new)**

- Test happy path: event received → log written → marked processed
- Test idempotency: duplicate → skip
- Minimal: this consumer is a placeholder

**6.5 — TelegramNotificationConsumerTest (new)**

- Test milestone reached + Telegram configured → notification sent
- Test milestone reached + no Telegram → no notification, no error
- Test no milestone → no notification
- Test each milestone value (7, 14, 21, 30, 60, 90) via `@ParameterizedTest`
- Test idempotency: duplicate event → skip
- Mock: `TelegramNotificationSender`, `HabitStreakRepository`, `UserRepository`, `ProcessedEventRepository`

**6.6 — KafkaHabitEventPublisherTest (new)**

- Test publish sends to correct topic with correct partition key
- Mock `KafkaTemplate`, verify `send()` arguments

### Phase 7: Integration Tests

**7.1 — KafkaEventIT (new, extends BaseIT)**

End-to-end flow:
1. Create a habit
2. Complete the habit via POST endpoint
3. Wait for consumers to process (use `Awaitility` or `Thread.sleep` with assertion polling)
4. Verify streak is updated in DB
5. Verify `processed_events` has 3 entries (one per consumer group)

**7.2 — DLQ and retry IT**

- Configure a consumer to fail (mock dependency to throw)
- Verify message appears in DLQ topic after 3 retries
- Use `KafkaConsumer` in test to read from DLQ topic

**7.3 — Idempotency IT**

- Manually insert into `processed_events` for a consumer group
- Publish event
- Verify consumer skips (no DB changes, WARN logged)

**7.4 — Update existing HabitControllerIT**

- Verify habit completion still returns 201 immediately
- Verify streak is eventually updated (async — poll with timeout)

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Single `HabitCompletedEvent` for both completion and log deletion | Both trigger streak recalculation. One event type simplifies consumer logic. The event means "habit log state changed." |
| Fire-and-forget event publishing | FR-003: publishing failure must not block HTTP response. The DB write (habit log) is the source of truth. |
| No `HabitEventPublisher` domain port | Use cases inject `ApplicationEventPublisher` (Spring) directly. `KafkaHabitEventPublisher` listens via `@TransactionalEventListener(AFTER_COMMIT)`. A domain port would be dead code — use cases never call it. |
| Independent streak calculation in TelegramNotificationConsumer | Consumer groups are independent; no ordering guarantee between streak calculator and notifier. Recalculating in notifier ensures correctness. |
| `consumer_group` column in `processed_events` | Same event processed by 3 different consumers — each needs its own idempotency record. |
| Real Telegram adapter with config flag | Per spec clarification: not a stub. Full TelegramBots integration, disabled by default via `lifesync.telegram.enabled=false`. |
| JSON serialization for events | Per spec assumption: JSON for human readability and debugging. Spring Kafka JsonSerializer/Deserializer. |

## DB-to-Event Field Mapping

| HabitCompletedEvent Field | Source | Type |
|---------------------------|--------|------|
| eventId | UUID.randomUUID().toString() | String |
| habitId | habit.getId().value() | UUID |
| userId | SecurityContext principal | UUID |
| logDate | request body date | LocalDate |
| completionId | saved HabitLog id | UUID |
| occurredAt | Instant.now(clock) | Instant |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| V — Migration V17 modifies existing `processed_events` table | Need `consumer_group` column for multi-consumer idempotency. Original V11 has unique on `event_id` alone, which would block the 2nd and 3rd consumer from registering the same event. | Adding a separate table per consumer would violate DRY and scatter idempotency logic. |
