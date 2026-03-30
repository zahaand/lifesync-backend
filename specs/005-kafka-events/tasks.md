# Tasks: Kafka Event-Driven Architecture

**Branch**: `005-kafka-events` | **Date**: 2026-03-30
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Checklist**: [checklists/implementation-quality.md](checklists/implementation-quality.md)

---

## Phase 0: Database Migration & Domain Events (7 tasks)

- [x] T001 — Create V17 migration: add `consumer_group` column to `processed_events`
  - File: `lifesync-infrastructure/src/main/resources/db/changelog/system/V17__add_consumer_group_to_processed_events.xml`
  - `<addColumn>` — `consumer_group varchar(100) NOT NULL DEFAULT 'unknown'`
  - `<dropUniqueConstraint>` — remove `uq_processed_events_event_id`
  - `<addUniqueConstraint>` — composite on `(event_id, consumer_group)`
  - `<createIndex>` — index on `(event_id, consumer_group)` for lookup queries
  - `<rollback>` — drop index, drop unique, add original unique on `event_id`, drop column
  - Validates: CHK002, CHK003, CHK059
  - Ref: Plan §0.1, Spec §FR-008/FR-009

- [x] T002 — Register V17 in changelog master
  - File: `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml`
  - Add `<include file="db/changelog/system/V17__add_consumer_group_to_processed_events.xml"/>`
  - Validates: CHK060
  - Depends: T001

- [x] T003 — Regenerate jOOQ classes after V17
  - Command: `mvn generate-sources -pl lifesync-infrastructure -Pjooq-codegen`
  - Verify `PROCESSED_EVENTS.CONSUMER_GROUP` field exists in generated code
  - Depends: T002

- [x] T004 [P] — Create `DomainEvent` sealed interface
  - File: `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/event/DomainEvent.java`
  - Sealed interface permitting `HabitCompletedEvent`, `GoalProgressUpdatedEvent`
  - Methods: `String eventId()`, `Instant occurredAt()`
  - Pure Java only — NO Spring, Kafka, Jackson imports
  - Validates: CHK054
  - Ref: Plan §0.2

- [x] T005 [P] — Create `HabitCompletedEvent` record
  - File: `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/event/HabitCompletedEvent.java`
  - Fields: `String eventId, UUID habitId, UUID userId, LocalDate logDate, UUID completionId, Instant occurredAt`
  - Implements `DomainEvent`
  - Pure Java only — NO Spring, Kafka, Jackson imports
  - Validates: CHK009, CHK054
  - Ref: Plan §0.2, Spec §FR-002

- [x] T006 [P] — Create `GoalProgressUpdatedEvent` stub record
  - File: `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/event/GoalProgressUpdatedEvent.java`
  - Fields: `String eventId, UUID goalId, UUID userId, UUID habitId, int progressPercentage, Instant occurredAt`
  - Implements `DomainEvent`
  - Pure Java only — NO Spring, Kafka, Jackson imports
  - Validates: CHK054, CHK069
  - Ref: Plan §0.2, Spec §FR-013

- [x] T007 [P] — Create `TelegramNotificationSender` domain port interface
  - File: `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/TelegramNotificationSender.java`
    - Method: `void send(String chatId, String message)`
    - NO TelegramBots types in signature
  - NOTE: `HabitEventPublisher` port is NOT created — use cases inject `ApplicationEventPublisher` (Spring) directly. `KafkaHabitEventPublisher` listens via `@TransactionalEventListener` and does not need a domain port.
  - Validates: CHK056
  - Ref: Plan §0.3

**Phase 0 checkpoint**: `mvn clean compile -pl lifesync-domain` passes. Domain module has zero Spring/Kafka/Jackson imports. V17 migration valid XML with rollback.

---

## Phase 1: Kafka Infrastructure & Configuration (8 tasks)

- [x] T008 — Create `KafkaTopicConfig` with topic beans
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/event/config/KafkaTopicConfig.java`
  - Topics via `TopicBuilder`:
    - `habit.log.completed` — 3 partitions, replication factor 1
    - `habit.log.completed.dlq` — 1 partition
    - `goal.progress.updated` — 3 partitions, replication factor 1
    - `goal.progress.updated.dlq` — 1 partition
  - `@Configuration` class, all fields final, constructor injection
  - Validates: CHK020, CHK070
  - Ref: Plan §1.1, Spec §FR-010/FR-014

- [x] T009 — Create `KafkaConsumerConfig` with retry and DLQ
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/event/config/KafkaConsumerConfig.java`
  - `ConcurrentKafkaListenerContainerFactory` bean
  - `DefaultErrorHandler` with `ExponentialBackOff(1000, 2.0)` maxAttempts=4 (1 original + 3 retries at intervals: 1s, 2s, 4s)
  - `DeadLetterPublishingRecoverer` routing to `{topic}.dlq`
  - Malformed events (deserialization errors) sent directly to DLQ without retry — configure `addNotRetryableExceptions(DeserializationException.class)`
  - ERROR-level logging on DLQ routing with full context (eventId, habitId, userId, topic, partition, offset, exception)
  - All fields final, constructor injection, no Lombok
  - Validates: CHK016, CHK017, CHK018, CHK019, CHK021
  - Ref: Plan §1.2, Spec §FR-011/FR-012/FR-015

- [x] T010 — Update `application.yml` with Kafka and Telegram config
  - File: `lifesync-app/src/main/resources/application.yml`
  - Add under `spring.kafka`:
    - `producer.key-serializer: StringSerializer`
    - `producer.value-serializer: JsonSerializer`
    - `consumer.auto-offset-reset: earliest`
    - `consumer.key-deserializer: StringDeserializer`
    - `consumer.value-deserializer: JsonDeserializer`
    - `consumer.properties.spring.json.trusted.packages: ru.zahaand.lifesync.domain.event`
  - Add `lifesync.telegram.enabled: ${TELEGRAM_ENABLED:false}`
  - Add `lifesync.telegram.bot-token: ${TELEGRAM_BOT_TOKEN:}`
  - Add `lifesync.telegram.bot-username: ${TELEGRAM_BOT_USERNAME:}`
  - Ref: Plan §1.2

- [x] T011 — Update `.env.example` with Telegram environment variables
  - File: `.env.example`
  - Add:
    - `TELEGRAM_ENABLED=false`
    - `TELEGRAM_BOT_TOKEN=<your-bot-token>`
    - `TELEGRAM_BOT_USERNAME=<your-bot-username>`
  - Validates: CHK046
  - Ref: Constitution §VI

- [x] T012 — Create `KafkaHabitEventPublisher`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/event/KafkaHabitEventPublisher.java`
  - `@Component` class — does NOT implement any domain port (use cases publish via `ApplicationEventPublisher` directly)
  - Listens with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with parameter type `HabitCompletedEvent` directly (Spring's `ApplicationEventPublisher.publishEvent()` supports arbitrary objects since Spring 4.2 — no `ApplicationEvent` wrapper class needed)
  - Sends to topic `habit.log.completed` with partition key `event.habitId().toString()`
  - Fire-and-forget with `CompletableFuture` callback:
    - On success: log DEBUG
    - On failure: log ERROR with habitId, userId, error message — do NOT rethrow
  - All fields final, constructor injection, no Lombok
  - Validates: CHK010, CHK011, CHK032, CHK057
  - Ref: Plan §1.3, Spec §FR-001/FR-003

- [x] T013 — Create `ProcessedEventRepository`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/event/ProcessedEventRepository.java`
  - Uses jOOQ generated `PROCESSED_EVENTS` table
  - `boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup)` — idempotency check
  - `void save(String eventId, String eventType, String consumerGroup)` — insert new record
  - On DB failure: let exception propagate (NOT caught) — consumer retry/DLQ handles it
  - All fields final, constructor injection, no Lombok
  - Validates: CHK006, CHK007
  - Depends: T003
  - Ref: Plan §1.4

- [x] T014 [P] — Add TelegramBots dependency to infrastructure pom.xml
  - File: `lifesync-infrastructure/pom.xml`
  - Add `org.telegram:telegrambots` (check latest version compatible with Spring Boot 3.5.x)
  - Scoped to `lifesync-infrastructure` only — not in domain or application
  - Validates: CHK047
  - Ref: Plan §4.2

- [x] T015 [P] — Update test `application.yml` with Kafka consumer config
  - File: `lifesync-web/src/test/resources/application.yml`
  - Mirror main `application.yml` Kafka serialization and trusted packages config
  - Set `lifesync.telegram.enabled: false` for tests
  - Ref: Plan §1.2

**Phase 1 checkpoint**: `mvn clean compile -pl lifesync-infrastructure` passes. All config classes compile. No runtime verification yet (consumers not created).

---

## Phase 2: Use Case Refactor (5 tasks)

- [x] T016 — Refactor `CompleteHabitUseCase`: remove sync streak, add event publishing
  - File: `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/CompleteHabitUseCase.java`
  - Remove constructor params: `HabitStreakRepository`, `StreakCalculatorService`
  - Remove `recalculateStreak()` private method entirely
  - Add constructor param: `ApplicationEventPublisher` (Spring)
  - After saving HabitLog, build `HabitCompletedEvent` and publish via `applicationEventPublisher.publishEvent()`
  - `@Transactional` remains on `execute()` — event fires AFTER commit via `@TransactionalEventListener`
  - Return `HabitLog` synchronously — event publishing outcome does not affect response
  - Validates: CHK024, CHK026, CHK030, CHK031
  - Depends: T004, T005, T007
  - Ref: Plan §2.1, Spec §FR-003/FR-004

- [x] T017 — Refactor `DeleteHabitLogUseCase`: remove sync streak, add event publishing
  - File: `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/DeleteHabitLogUseCase.java`
  - Same pattern as T016: remove `HabitStreakRepository`, `StreakCalculatorService`, `recalculateStreak()`
  - Add `ApplicationEventPublisher`, publish `HabitCompletedEvent` after soft-delete
  - Event semantically means "habit log state changed — recalculate streak"
  - The `HabitCompletedEvent` published on log deletion uses the deleted log's ID as `completionId`. This signals consumers that a log was removed and streak must be recalculated. Consumer logic handles both completion and deletion cases identically — recalculate streak from current logs.
  - Validates: CHK025, CHK027
  - Depends: T004, T005, T007
  - Ref: Plan §2.2

- [x] T018 — Update `UseCaseConfig` and `TestUseCaseConfig` bean wiring
  - File: `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/UseCaseConfig.java`
  - `completeHabitUseCase` bean: replace `HabitStreakRepository` + `StreakCalculatorService` with `ApplicationEventPublisher`
  - `deleteHabitLogUseCase` bean: same replacement
  - `streakCalculatorService` bean: KEEP (still used by `UpdateHabitUseCase` and consumers)
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/TestUseCaseConfig.java`
  - Also update `completeHabitUseCase` and `deleteHabitLogUseCase` bean definitions to match updated constructors (`ApplicationEventPublisher` replaces `HabitStreakRepository` + `StreakCalculatorService`)
  - Validates: CHK028, CHK029
  - Depends: T016, T017
  - Ref: Plan §2.3

- [x] T019 — Update `CompleteHabitUseCaseTest`
  - File: `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/CompleteHabitUseCaseTest.java`
  - Remove all streak recalculation assertions and mocks
  - Add mock `ApplicationEventPublisher`
  - Verify `publishEvent()` called with `HabitCompletedEvent` containing correct habitId, userId, logDate, completionId
  - Add test: when `publishEvent()` throws, `execute()` still returns saved `HabitLog` (no exception propagation)
  - `@ExtendWith(MockitoExtension.class)`, `@DisplayName` first, `@Nested` per method
  - Validates: CHK033, CHK061, CHK062, CHK063
  - Depends: T016
  - Ref: Plan §6.1

- [x] T020 — Update `DeleteHabitLogUseCaseTest`
  - File: `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/DeleteHabitLogUseCaseTest.java`
  - Same pattern as T019: remove streak mocks, add event publishing verification
  - `@ExtendWith(MockitoExtension.class)`, `@DisplayName` first, `@Nested` per method
  - Validates: CHK061, CHK062, CHK063
  - Depends: T017
  - Ref: Plan §6.2

**Phase 2 checkpoint**: `mvn clean verify -pl lifesync-application` passes. All unit tests green. No streak dependencies in CompleteHabitUseCase or DeleteHabitLogUseCase.

---

## Phase 3: Kafka Consumers (6 tasks)

- [x] T021 — Create `StreakCalculatorConsumer`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/StreakCalculatorConsumer.java`
  - `@KafkaListener(topics = "habit.log.completed", groupId = "lifesync-streak-calculator")`
  - Dependencies: `HabitRepository`, `HabitLogRepository`, `HabitStreakRepository`, `StreakCalculatorService`, `ProcessedEventRepository`
  - Processing flow:
    1. Log DEBUG: topic, partition, offset (from `ConsumerRecordMetadata` or `@Header`)
    2. Idempotency check: `processedEventRepository.existsByEventIdAndConsumerGroup(eventId, "lifesync-streak-calculator")`
       - If exists: log WARN "Duplicate event {eventId}, skipping" → return
       - If DB unavailable: exception propagates → retry/DLQ
    3. Fetch habit by `habitId` + `userId`
    4. Fetch log dates via `habitLogRepository.findLogDatesDesc()`
    5. Calculate streak via `streakCalculatorService.calculate()`
    6. Save/update streak in `habitStreakRepository`
    7. Register in `processedEventRepository.save(eventId, "HabitCompletedEvent", "lifesync-streak-calculator")`
    8. Log INFO: "Streak recalculated: habitId={}, currentStreak={}, longestStreak={}"
  - All fields final, constructor injection, no Lombok
  - Validates: CHK001, CHK004, CHK005, CHK006, CHK008, CHK048, CHK049, CHK050, CHK051, CHK052, CHK058, CHK068
  - Depends: T003, T008, T009, T012, T013
  - Ref: Plan §3.1, Spec §FR-004/FR-008/FR-009/FR-016/FR-017

- [x] T022 — Create `StreakCalculatorConsumerTest`
  - File: `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/habit/StreakCalculatorConsumerTest.java`
  - `@ExtendWith(MockitoExtension.class)` — no Spring context
  - `@Nested` per method, `@DisplayName` first on every test
  - Tests:
    - Happy path: event → streak recalculated → saved → marked processed
    - Idempotency: duplicate eventId → WARN logged, no streak recalculation, no save
    - Habit not found: exception → propagates (triggers retry/DLQ)
    - DB failure on idempotency check: exception → propagates (triggers retry/DLQ)
  - Mock: `ProcessedEventRepository`, `HabitRepository`, `HabitLogRepository`, `HabitStreakRepository`, `StreakCalculatorService`
  - Use `Clock.fixed()` for streak assertions
  - Validates: CHK061, CHK062, CHK063, CHK065
  - Depends: T021
  - Ref: Plan §6.3

- [x] T023 — Create `AnalyticsUpdaterConsumer`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/AnalyticsUpdaterConsumer.java`
  - `@KafkaListener(topics = "habit.log.completed", groupId = "lifesync-analytics-updater")`
  - Dependencies: `ProcessedEventRepository`
  - Processing flow (placeholder per FR-005):
    1. Log DEBUG: topic, partition, offset
    2. Idempotency check with consumer group `"lifesync-analytics-updater"`
    3. Log INFO: "Analytics cache invalidated: userId={}, habitId={}"
    4. Register in `processedEventRepository.save()`
  - All fields final, constructor injection, no Lombok
  - Validates: CHK001, CHK005, CHK048, CHK049, CHK050, CHK051, CHK052, CHK068
  - Depends: T008, T009, T013
  - Ref: Plan §3.2, Spec §FR-005

- [x] T024 — Create `AnalyticsUpdaterConsumerTest`
  - File: `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/habit/AnalyticsUpdaterConsumerTest.java`
  - `@ExtendWith(MockitoExtension.class)`, `@Nested` per method, `@DisplayName` first
  - Tests:
    - Happy path: event → log written → marked processed
    - Idempotency: duplicate → skip
  - Minimal: consumer is a placeholder
  - Validates: CHK061, CHK062, CHK063
  - Depends: T023
  - Ref: Plan §6.4

- [x] T025 — Create `TelegramNotificationConsumer`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/TelegramNotificationConsumer.java`
  - `@KafkaListener(topics = "habit.log.completed", groupId = "lifesync-telegram-notifier")`
  - Dependencies: `HabitRepository`, `HabitLogRepository`, `StreakCalculatorService`, `UserRepository`, `TelegramNotificationSender`, `ProcessedEventRepository`
  - Milestone thresholds: `private static final Set<Integer> MILESTONES = Set.of(7, 14, 21, 30, 60, 90);`
  - Processing flow:
    1. Log DEBUG: topic, partition, offset
    2. Idempotency check with consumer group `"lifesync-telegram-notifier"`
    3. Recalculate streak independently (fetch habit, log dates, call `streakCalculatorService.calculate()`)
       — NOT reading from `habitStreakRepository` (avoids ordering dependency with StreakCalculatorConsumer)
    4. Check if `currentStreak` is in `MILESTONES`
       - If not: log DEBUG "No milestone: habitId={}, streak={}" → register + return
    5. Fetch user from `userRepository.findById(userId)`
    6. Check `user.getProfile().telegramChatId()` is not null/blank
       - If null/blank: log DEBUG "No Telegram for userId={}" → register + return
    7. Build message: "You've reached a {N}-day streak! Keep going!"
    8. Call `telegramNotificationSender.send(chatId, message)`
    9. Register in `processedEventRepository.save()`
    10. Log INFO: "Telegram milestone notification sent: userId={}, streak={}"
  - All fields final, constructor injection, no Lombok
  - Validates: CHK001, CHK005, CHK040, CHK041, CHK042, CHK043, CHK048, CHK049, CHK050, CHK051, CHK052, CHK068
  - Depends: T007, T008, T009, T013
  - Ref: Plan §3.3, Spec §FR-006/FR-007

- [x] T026 — Create `TelegramNotificationConsumerTest`
  - File: `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/notification/TelegramNotificationConsumerTest.java`
  - `@ExtendWith(MockitoExtension.class)`, `@Nested` per method, `@DisplayName` first
  - Tests:
    - Milestone reached + Telegram configured → `send()` called with correct chatId and message
    - Milestone reached + no telegramChatId → `send()` NOT called, no error
    - No milestone (streak=6) → `send()` NOT called
    - `@ParameterizedTest` + `@MethodSource` for all 6 milestones: 7, 14, 21, 30, 60, 90
    - Idempotency: duplicate eventId → skip
    - Telegram adapter throws → exception propagates (retry/DLQ)
  - Mock: `HabitRepository`, `HabitLogRepository`, `StreakCalculatorService`, `UserRepository`, `TelegramNotificationSender`, `ProcessedEventRepository`
  - Use `Clock.fixed()` for streak calculations
  - Validates: CHK045, CHK061, CHK062, CHK063, CHK065
  - Depends: T025
  - Ref: Plan §6.5

**Phase 3 checkpoint**: `mvn clean compile -pl lifesync-infrastructure` passes. Unit tests: `mvn test -pl lifesync-infrastructure` all green.

---

## Phase 4: Telegram Adapter (2 tasks)

- [ ] T027 — Create `TelegramNotificationAdapter`
  - File: `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/TelegramNotificationAdapter.java`
  - Implements `TelegramNotificationSender` domain port
  - Inject `@Value("${lifesync.telegram.enabled}") boolean enabled`
  - Inject `@Value("${lifesync.telegram.bot-token}") String botToken`
  - When `enabled=true`:
    - Use TelegramBots library to send `SendMessage` to chatId
    - On failure: throw exception (consumer retry/DLQ handles it)
  - When `enabled=false`:
    - Log INFO: "Telegram disabled, would send to chatId={}: {message}"
    - No API call
  - All fields final, constructor injection, no Lombok
  - Validates: CHK036, CHK037, CHK038, CHK039, CHK048, CHK049, CHK050
  - Depends: T014
  - Ref: Plan §4.1

- [ ] T028 — Create `KafkaHabitEventPublisherTest`
  - File: `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/event/KafkaHabitEventPublisherTest.java`
  - `@ExtendWith(MockitoExtension.class)`, `@Nested` per method, `@DisplayName` first
  - Tests:
    - Publish sends to correct topic `habit.log.completed`
    - Partition key is `event.habitId().toString()`
    - Kafka failure: ERROR logged, exception NOT propagated
  - Mock: `KafkaTemplate`
  - Validates: CHK010, CHK011, CHK032, CHK061, CHK062, CHK063
  - Depends: T012
  - Ref: Plan §6.6

**Phase 4 checkpoint**: `mvn clean test -pl lifesync-infrastructure` passes. All unit tests green including publisher and adapter compilation.

---

## Phase 5: Test Infrastructure (3 tasks)

- [ ] T029 — Add `testcontainers-kafka` dependency to web module
  - File: `lifesync-web/pom.xml`
  - Add:
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <scope>test</scope>
    </dependency>
    ```
  - Ref: Plan §5.1

- [ ] T030 — Update `BaseIT` with `KafkaContainer` Testcontainer
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/BaseIT.java`
  - Add `@Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))`
  - Update `@DynamicPropertySource`:
    - Replace hardcoded `localhost:9092` with `KAFKA::getBootstrapServers`
  - Add `lifesync.telegram.enabled=false` to test properties
  - Validates: CHK064
  - Depends: T029
  - Ref: Plan §5.1

- [ ] T031 — Verify existing integration tests pass with KafkaContainer
  - Command: `mvn clean verify -pl lifesync-web`
  - All existing ITs (AuthControllerIT, UserControllerIT, AdminControllerIT, HabitControllerIT) must pass
  - HabitControllerIT may need adjustment: streak is now async (see T032)
  - Depends: T030, T018
  - Ref: Plan §5.1

**Phase 5 checkpoint**: `mvn clean verify -pl lifesync-web` passes. BaseIT uses Kafka Testcontainer. All existing tests green.

---

## Phase 6: Integration Tests (5 tasks)

- [ ] T032 — Update `HabitControllerIT`: async streak verification
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/habit/HabitControllerIT.java`
  - Habit completion tests: POST still returns 201 immediately
  - Streak assertions: change from immediate check to polling with `Awaitility.await().atMost(10, SECONDS).untilAsserted(...)` or similar
  - Verify streak is eventually updated (async consumer processed event)
  - Validates: CHK034
  - Depends: T030, T021
  - Ref: Plan §7.4

- [ ] T033 — Create `KafkaEventIT`: end-to-end event flow
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/event/KafkaEventIT.java`
  - Extends `BaseIT`
  - Test flow:
    1. Register user, login, create active habit
    2. Complete habit via `POST /api/v1/habits/{id}/complete`
    3. Verify 201 response returned immediately
    4. Poll DB: streak updated (currentStreak >= 1)
    5. Poll DB: `processed_events` has 3 entries (one per consumer group: `lifesync-streak-calculator`, `lifesync-analytics-updater`, `lifesync-telegram-notifier`)
  - `@DisplayName` first, `@Nested` grouping
  - Validates: CHK004, CHK064
  - Depends: T030, T021, T023, T025
  - Ref: Plan §7.1, Spec §SC-002/SC-003

- [ ] T034 — Create idempotency integration test in `KafkaEventIT`
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/event/KafkaEventIT.java` (add `@Nested` class)
  - Test flow:
    1. Pre-insert a `processed_events` record for event_id + consumer group
    2. Publish `HabitCompletedEvent` with same event_id
    3. Verify consumer skips: no streak change, no duplicate `processed_events` row
  - Validates: CHK001, CHK008
  - Depends: T033
  - Ref: Plan §7.3, Spec §SC-003

- [ ] T035 — Create DLQ integration test in `KafkaEventIT`
  - File: `lifesync-web/src/test/java/ru/zahaand/lifesync/web/event/KafkaEventIT.java` (add `@Nested` class)
  - Test flow:
    1. Publish event that will cause consumer to fail (e.g., non-existent habitId → HabitNotFoundException)
    2. Wait for 3 retry attempts (exponential backoff: ~7s total)
    3. Read from `habit.log.completed.dlq` topic with test `KafkaConsumer`
    4. Verify original message payload is present in DLQ
  - Validates: CHK016, CHK022
  - Depends: T033
  - Ref: Plan §7.2, Spec §SC-004

- [ ] T036 — Verify full build passes with JaCoCo
  - Command: `mvn clean verify`
  - All unit tests and integration tests pass
  - JaCoCo ≥ 80% on `lifesync-domain` and `lifesync-application`
  - Validates: CHK066
  - Depends: T032, T033, T034, T035
  - Ref: Constitution §X

**Phase 6 checkpoint**: `mvn clean verify` passes. All ITs green. JaCoCo ≥ 80%.

---

## Dependency Graph

```
T001 → T002 → T003 ──────────────────────────────────┐
T004 ─┬─ (parallel) ─┐                                │
T005 ─┤               ├─ T016 ─┬─ T018 ─── T031       │
T006 ─┤               │  T017 ─┘    │       │          │
T007 ─┘               │             │       │          │
                      │  T019 (after T016)  │          │
                      │  T020 (after T017)  │          │
T008 ─┐               │                     │          │
T009 ─┤               │                     │          │
T010 ─┤ (parallel)    │                     │          │
T011 ─┤               │                     │          │
T014 ─┤               │                     │          │
T015 ─┘               │                     │          │
      │               │                     │          │
T003 ─┼── T012        │                     │          │
      │   T013 ───────┘                     │          │
      │               │                     │          │
      ├── T021 → T022 │                     │          │
      ├── T023 → T024 │                     │          │
      ├── T025 → T026 │                     │          │
      │               │                     │          │
      ├── T027 (after T014)                 │          │
      ├── T028 (after T012)                 │          │
      │                                     │          │
      │   T029 → T030 → T031 ──────────────┘          │
      │                  │                              │
      │                  ├── T032 (after T021)          │
      │                  ├── T033 (after T021,T023,T025)│
      │                  ├── T034 (after T033)          │
      │                  ├── T035 (after T033)          │
      │                  └── T036 (after T032–T035)     │
```

## Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| 0 | T001–T007 (7) | V17 migration, domain events, port |
| 1 | T008–T015 (8) | Kafka topics, retry/DLQ config, publisher, idempotency repo, .env.example |
| 2 | T016–T020 (5) | Use case refactor + unit tests |
| 3 | T021–T026 (6) | 3 consumers + unit tests |
| 4 | T027–T028 (2) | Telegram adapter + publisher test |
| 5 | T029–T031 (3) | Test infrastructure + baseline verification |
| 6 | T032–T036 (5) | Integration tests + JaCoCo |
| **Total** | **36 tasks** | |
