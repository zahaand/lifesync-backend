# Implementation Quality Checklist: Kafka Event-Driven Architecture

**Purpose**: Validate requirements completeness, clarity, and consistency across 6 focus areas before Sprint 5 implementation begins
**Created**: 2026-03-30
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

## Idempotency

- [ ] CHK001 - Do ALL 3 consumers (StreakCalculatorConsumer, AnalyticsUpdaterConsumer, TelegramNotificationConsumer) call `processedEventRepository.existsByEventIdAndConsumerGroup()` BEFORE any business logic? [Completeness, Plan §3.1–3.3, Spec §FR-008]
- [ ] CHK002 - Does the `processed_events` table have a composite unique constraint on `(event_id, consumer_group)` after V17, allowing the same event_id to exist three times (once per consumer group)? [Correctness, Plan §0.1]
- [ ] CHK003 - Is the V17 migration rollback block correctly specified — does it restore the original single-column unique constraint on `event_id` and drop the `consumer_group` column? [Completeness, Constitution §V, Plan §0.1]
- [ ] CHK004 - Do ALL 3 consumers insert into `processed_events` AFTER successful processing and BEFORE returning, so a crash between processing and insert does not silently lose the idempotency record? [Ordering, Plan §3.1–3.3, Spec §FR-009]
- [ ] CHK005 - Does each consumer pass its own consumer group string (e.g., `"lifesync-streak-calculator"`) to `existsByEventIdAndConsumerGroup()` and `save()`, not a shared constant? [Isolation, Plan §3.1–3.3]
- [ ] CHK006 - Is the behavior when `processed_events` table is unavailable (DB down) defined — does the consumer fail and trigger retry/DLQ, or process without idempotency? The spec edge case says "fail and rely on retry/DLQ." Is this enforced in the plan? [Edge Case, Spec §Edge Cases, Critical]
- [ ] CHK007 - Is the `ProcessedEventRepository.save()` call inside the same database transaction as the business write (streak update), or are they separate transactions? If separate, is the gap between business write and idempotency insert acceptable? [Consistency, Gap]
- [ ] CHK008 - Does the idempotency check log at WARN level when skipping a duplicate, per Constitution §VIII and Spec §FR-008? [Logging, Constitution §VIII]
- [ ] CHK009 - Is the `event_id` format defined — UUID string from `HabitCompletedEvent.eventId()`? Is there a risk of collision between events from `CompleteHabitUseCase` and `DeleteHabitLogUseCase`? [Clarity, Plan §2.1–2.2]

## Event Ordering & Partitioning

- [ ] CHK010 - Is `habitId` used as the Kafka partition key in `KafkaHabitEventPublisher`, guaranteeing that all events for the same habit land on the same partition? [Correctness, Spec §FR-001, Plan §1.3]
- [ ] CHK011 - Is the partition key set via `event.habitId().toString()`, not `event.userId().toString()` or `event.eventId()`? [Clarity, Plan §1.3]
- [ ] CHK012 - With 3 partitions and `habitId` as partition key, is there a risk of skewed distribution if one user has many habits? Is this acceptable for the current scale? [Scalability, Plan §1.1]
- [ ] CHK013 - Does partition-level ordering guarantee that a "complete" event followed by a "delete log" event for the same habit are processed in order by the StreakCalculatorConsumer? Or are they independent events that may interleave? [Ordering, Plan §Key Design Decisions]
- [ ] CHK014 - Is it documented that independent consumer groups (streak, analytics, telegram) each maintain their own offset — so ordering is guaranteed WITHIN a single consumer group but NOT across groups? [Clarity, Plan §3.3]
- [ ] CHK015 - If `DeleteHabitLogUseCase` also publishes `HabitCompletedEvent`, does the consumer differentiate between "log created" and "log deleted" scenarios? Is the event payload sufficient for streak recalculation regardless of which triggered it? [Ambiguity, Plan §2.2, Key Design Decisions]

## Dead Letter Queue & Retry

- [ ] CHK016 - Is the `DefaultErrorHandler` configured with exactly 3 retry attempts and exponential backoff intervals of 1s, 2s, 4s as specified? [Correctness, Spec §FR-011, Plan §1.2]
- [ ] CHK017 - Does the `DeadLetterPublishingRecoverer` route to `{original-topic}.dlq` (e.g., `habit.log.completed.dlq`), not a generic DLQ topic? [Correctness, Spec §FR-010, Plan §1.1]
- [ ] CHK018 - Is ERROR-level logging with full event context triggered when a message is routed to DLQ? Does "full context" include eventId, habitId, userId, topic, partition, offset, and exception message? [Completeness, Spec §FR-012]
- [ ] CHK019 - Are malformed events (deserialization failure, missing required fields) sent directly to DLQ WITHOUT retry, per Spec §FR-015? Is this configured separately from the 3-retry logic for processing failures? [Correctness, Spec §FR-015, Gap]
- [ ] CHK020 - Is the `goal.progress.updated.dlq` topic created even though no consumers exist yet for `goal.progress.updated`? [Completeness, Spec §FR-014, Plan §1.1]
- [ ] CHK021 - Does the DLQ message retain the original message headers (partition key, timestamp, event payload), so operators can inspect and replay? [Operability, Gap]
- [ ] CHK022 - Is the retry behavior tested via integration test — specifically, is there a test that forces a consumer to fail 3 times and verifies the message appears in the DLQ topic? [Testability, Plan §7.2]
- [ ] CHK023 - If a consumer succeeds on retry (e.g., fails first attempt, succeeds second), is the idempotency record inserted only on success, not on the failed attempts? [Correctness, Plan §3.1]

## Async vs Sync Migration

- [ ] CHK024 - Is the `recalculateStreak()` private method COMPLETELY removed from `CompleteHabitUseCase`, not just commented out or bypassed? [Completeness, Plan §2.1, Spec §FR-004]
- [ ] CHK025 - Is the `recalculateStreak()` private method COMPLETELY removed from `DeleteHabitLogUseCase`? [Completeness, Plan §2.2]
- [ ] CHK026 - Are `HabitStreakRepository` and `StreakCalculatorService` dependencies REMOVED from the `CompleteHabitUseCase` constructor, not just unused? [Cleanup, Plan §2.1]
- [ ] CHK027 - Are `HabitStreakRepository` and `StreakCalculatorService` dependencies REMOVED from the `DeleteHabitLogUseCase` constructor? [Cleanup, Plan §2.2]
- [ ] CHK028 - Is the `UseCaseConfig` bean wiring updated to match the new constructor signatures — `HabitEventPublisher` replaces `HabitStreakRepository` + `StreakCalculatorService` in both use case beans? [Consistency, Plan §2.3]
- [ ] CHK029 - Does `StreakCalculatorService` bean REMAIN in `UseCaseConfig` (still used by `StreakCalculatorConsumer` and `UpdateHabitUseCase`)? [Correctness, Plan §2.3]
- [ ] CHK030 - Is the event publishing call placed OUTSIDE the `@Transactional` boundary (or wrapped in try-catch), so a Kafka failure does not roll back the DB write? [Critical, Spec §FR-003, Plan §2.1]
- [ ] CHK031 - Does `CompleteHabitUseCase` still return `HabitLog` synchronously to the controller, confirming the DB write succeeded regardless of event publishing outcome? [Correctness, Spec §FR-003]
- [ ] CHK032 - Is the event publishing failure logged at ERROR level but NOT thrown, per FR-003? Does the HTTP response remain 201 even when Kafka is unreachable? [Critical, Spec §FR-003, Edge Cases]
- [ ] CHK033 - Is there a test verifying that `CompleteHabitUseCase` succeeds (returns HabitLog) when `HabitEventPublisher.publish()` throws an exception? [Testability, Plan §6.1]
- [ ] CHK034 - Does the existing `HabitControllerIT` test for habit completion now verify that the streak is EVENTUALLY updated (async polling) rather than IMMEDIATELY after the POST response? [Migration, Plan §7.4]
- [ ] CHK035 - Is the `UpdateHabitUseCase` still calling `StreakCalculatorService` synchronously for frequency changes, or is that also migrated to async? The plan says it remains sync — is this intentional? [Clarity, Plan §2.3, Gap]

## Telegram Adapter

- [ ] CHK036 - Does `TelegramNotificationAdapter` implement the `TelegramNotificationSender` domain port interface, not a Spring-specific interface? [Hexagonal, Constitution §I, Plan §4.1]
- [ ] CHK037 - When `lifesync.telegram.enabled=false`, does the adapter log at INFO level "Telegram disabled, would send to chatId={}: {message}" without making any API call? [Correctness, Spec §FR-006, Plan §4.1]
- [ ] CHK038 - When `lifesync.telegram.enabled=true`, does the adapter use the TelegramBots library with bot token and username from environment variables? [Correctness, Plan §4.1, Constitution §VI]
- [ ] CHK039 - When the adapter fails (Telegram API unreachable), does it throw an exception that triggers the consumer's retry/DLQ mechanism? [Error Handling, Spec §Edge Cases, Plan §4.1]
- [ ] CHK040 - Does the `TelegramNotificationConsumer` recalculate the streak independently (injecting `HabitRepository`, `HabitLogRepository`, `StreakCalculatorService`) rather than reading from `habitStreakRepository`? This is the plan's chosen approach to avoid ordering dependency with `StreakCalculatorConsumer`. [Correctness, Plan §3.3 Revised approach]
- [ ] CHK041 - Does the consumer check ALL 6 milestone values (7, 14, 21, 30, 60, 90) — not just a subset? Are these defined as a constant `Set`, not hardcoded in a chain of if-statements? [Completeness, Spec §FR-006, Plan §3.3]
- [ ] CHK042 - When no milestone is reached, does the consumer skip silently (log DEBUG, mark as processed, return) with NO error and NO notification attempt? [Correctness, Spec §FR-007]
- [ ] CHK043 - When the user has no `telegramChatId` (null or blank), does the consumer skip silently with NO error, even if a milestone was reached? [Correctness, Spec §FR-007]
- [ ] CHK044 - Is the congratulation message format defined, or is it left to implementation? Does it include the streak count and habit name? [Clarity, Gap]
- [ ] CHK045 - Is there a `@ParameterizedTest` covering all 6 milestone values (7, 14, 21, 30, 60, 90) in `TelegramNotificationConsumerTest`? [Testability, Plan §6.5, Constitution §X]
- [ ] CHK046 - Are the Telegram bot token and username environment variables (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`) documented and added to `.env.example` or similar? [Operability, Constitution §VI]
- [ ] CHK047 - Is the TelegramBots library version compatible with Spring Boot 3.5.x? Is the dependency scoped to `lifesync-infrastructure` module only? [Compatibility, Plan §4.2, Constitution §I]

## Constitution Compliance

- [ ] CHK048 - Do ALL new classes (consumers, publisher, adapter, repository, event records) use explicit constructors with constructor injection? No `@Autowired` on fields? [Constitution §IX]
- [ ] CHK049 - Are ALL fields in new classes declared `final`? [Constitution §IX]
- [ ] CHK050 - Is there ZERO Lombok usage in any new file — no `@Slf4j`, no `@RequiredArgsConstructor`, no `@Data`? [Constitution §VII]
- [ ] CHK051 - Do ALL 3 consumers log at DEBUG level: topic, partition, offset BEFORE processing each event? Is the log format consistent across consumers? [Constitution §VIII, Spec §FR-016]
- [ ] CHK052 - Do ALL 3 consumers log at INFO level upon successful processing with business context (habitId, userId, result)? [Constitution §VIII, Spec §FR-017]
- [ ] CHK053 - Are sensitive data fields (tokens, passwords) excluded from all log statements? Events contain userId and habitId (acceptable), not tokens. [Constitution §VIII]
- [ ] CHK054 - Are ALL domain event classes (`DomainEvent`, `HabitCompletedEvent`, `GoalProgressUpdatedEvent`) pure Java with NO Spring, jOOQ, Kafka, or Jackson imports? [Constitution §I]
- [ ] CHK055 - N/A — `HabitEventPublisher` domain port removed. Use cases inject `ApplicationEventPublisher` (Spring) directly; `KafkaHabitEventPublisher` listens via `@TransactionalEventListener`. [Constitution §I, Plan §0.3]
- [ ] CHK056 - Is the `TelegramNotificationSender` port interface in `lifesync-domain` with NO TelegramBots-specific types in its signature? [Constitution §I]
- [ ] CHK057 - Are ALL Kafka infrastructure classes (`KafkaHabitEventPublisher`, consumers, `KafkaTopicConfig`, `KafkaConsumerConfig`) in `lifesync-infrastructure` module only? [Constitution §I]
- [ ] CHK058 - Is `@Transactional` used ONLY in `lifesync-application` module use cases, NOT in consumers or infrastructure code? [Constitution §Dev Standards 11]
- [ ] CHK059 - Does the `processed_events` V17 migration have a `<rollback>` block? [Constitution §V]
- [ ] CHK060 - Is the V17 migration file registered in `db.changelog-master.xml`? [Constitution §V]
- [ ] CHK061 - Are ALL new test classes using `@ExtendWith(MockitoExtension.class)` for unit tests (no Spring context)? [Constitution §X]
- [ ] CHK062 - Do ALL new test methods have `@DisplayName` as the first annotation? [Constitution §X]
- [ ] CHK063 - Are test classes organized with `@Nested` per method being tested? [Constitution §X]
- [ ] CHK064 - Do integration tests extend `BaseIT` with Testcontainers (PostgreSQL + Kafka), not external services? [Constitution §X, Plan §5.1]
- [ ] CHK065 - Is `Clock.fixed()` used in streak-related tests, never `Instant.now()` or `LocalDate.now()`? [Constitution §X]
- [ ] CHK066 - Is JaCoCo coverage ≥ 80% on domain + application modules after Sprint 5 changes? [Constitution §X]
- [ ] CHK067 - Are ALL identifiers (class names, method names, variables, package names) in English? [Constitution §XI]
- [ ] CHK068 - Are Kafka consumer groups following the naming pattern `lifesync-{consumer-name}` as stated in spec assumptions? [Spec §Assumptions]
- [ ] CHK069 - Does the `GoalProgressUpdatedEvent` stub contain ALL required fields: goalId, userId, habitId, progressPercentage, occurredAt? [Completeness, Spec §FR-013]
- [ ] CHK070 - Is the `goal.progress.updated` topic configured with 3 partitions and its DLQ topic exists, even though no consumers are implemented? [Completeness, Spec §FR-014]

## Notes

- Items marked `[Gap]` indicate requirements that may need to be added to the spec or plan before implementation
- Items marked `[Ambiguity]` highlight wording that could lead to divergent implementations
- Items marked `[Critical]` should be resolved before task generation — they affect correctness of the async migration
- Cross-references use: `Spec §` for spec.md, `Plan §` for plan.md, `Constitution §` for constitution.md
