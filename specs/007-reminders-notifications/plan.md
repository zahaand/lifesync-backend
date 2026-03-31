# Implementation Plan: Reminders & Notifications

**Branch**: `007-reminders-notifications` | **Date**: 2026-03-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/007-reminders-notifications/spec.md`

## Summary

Habit reminders via scheduled job (every minute), goal progress milestone notifications (25/50/75/100%), habit streak message enhancement, timezone-aware scheduling. Replaces GoalNotificationConsumer stub with real implementation. Enhances TelegramNotificationConsumer message format. Two new Liquibase migrations for `sent_reminders` and `goal_sent_milestones` tables. New `HabitReminderScheduler` (@Scheduled) in infrastructure. Reuses existing TelegramNotificationSender, ProcessedEventRepository, and Kafka infrastructure.

## Technical Context

**Language/Version**: Java 21 LTS
**Primary Dependencies**: Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, commons-lang3 3.17.0
**Storage**: PostgreSQL 16 (existing tables + 2 new: `sent_reminders`, `goal_sent_milestones`)
**Testing**: JUnit 5 + Mockito + Testcontainers (PostgreSQL + Kafka)
**Target Platform**: Linux server (Docker Compose for local dev)
**Project Type**: Web service (REST API with Kafka event-driven consumers + scheduler)
**Performance Goals**: Scheduler completes within 60s per run, Telegram send < 2s per message
**Constraints**: User data isolation (userId on all queries), JaCoCo >= 80%
**Scale/Scope**: Single-user B2C habit/goal tracking platform

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Design Gate (all pass)

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | SentReminderRepository port in domain. JooqSentReminderRepository + HabitReminderScheduler in infrastructure. No Spring in domain. |
| II | API First | PASS | No new API endpoints in Sprint 7. No YAML changes needed. |
| III | User Data Isolation | PASS | sent_reminders includes user_id FK. Scheduler loads habits per user context. |
| IV | Single Responsibility | PASS | Scheduler finds + sends reminders. Consumer handles Kafka events. Separate repository for persistence. |
| V | Liquibase Migrations | PASS | V18 + V19: XML format, native tags, FK CASCADE, indexes, rollback blocks. |
| VI | Secrets via Env Vars | PASS | No new secrets. Existing telegram.bot-token via env. |
| VII | Portfolio Readability | PASS | No Lombok. All identifiers in English. No speculative features. |
| VIII | Logging Standards | PASS | Scheduler: DEBUG for params, INFO for sent count, WARN for Telegram failures, ERROR for DB failures. |
| IX | Code Style | PASS | Final fields, constructor injection, explicit constructors. |
| X | Testing Standards | PASS | Unit: MockitoExtension. IT: Testcontainers. @Nested per method. @DisplayName in English. |
| XI | Code & Doc Language | PASS | Code in English. Commits: Russian body, Conventional Commits type. |
| XII | OpenAPI Documentation | PASS | No new endpoints — no YAML changes needed. |

### Post-Design Gate (all pass)

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | Domain: SentReminderRepository, GoalSentMilestoneRepository (ports). Infrastructure: jOOQ implementations, HabitReminderScheduler, updated consumers. |
| III | User Data Isolation | PASS | sent_reminders has user_id. goal_sent_milestones linked to goal (which has user_id). |
| V | Liquibase Migrations | PASS | V18 sent_reminders, V19 goal_sent_milestones. Both follow Constitution V patterns exactly. |
| VIII | Logging Standards | PASS | Scheduler: DEBUG for habit count, INFO per reminder sent, WARN for Telegram failures. Consumers: DEBUG topic/partition/offset. |

## Project Structure

### Documentation (this feature)

```text
specs/007-reminders-notifications/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 research decisions
├── data-model.md        # Entity model
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
lifesync-infrastructure/src/main/resources/db/changelog/
├── notification/
│   └── V18__create_sent_reminders.xml          # NEW — habit reminder tracking
│   └── V19__create_goal_sent_milestones.xml    # NEW — goal milestone tracking
└── db.changelog-master.xml                      # MODIFY — add V18, V19 includes

lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/
├── notification/
│   ├── SentReminderRepository.java             # NEW — port interface
│   └── GoalSentMilestoneRepository.java        # NEW — port interface
└── habit/
    └── HabitRepository.java                     # MODIFY — add findHabitsForReminder()

lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/
├── notification/
│   ├── JooqSentReminderRepository.java         # NEW — jOOQ implementation
│   ├── JooqGoalSentMilestoneRepository.java    # NEW — jOOQ implementation
│   ├── HabitReminderScheduler.java             # NEW — @Scheduled cron every minute
│   └── TelegramNotificationConsumer.java       # MODIFY — enhance message format
└── goal/
    └── GoalNotificationConsumer.java            # MODIFY — replace stub with real impl

lifesync-domain/src/test/java/ru/zahaand/lifesync/domain/notification/
└── (no domain tests — ports are interfaces)

lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/notification/
└── HabitReminderSchedulerTest.java             # NEW — unit test with mocks

lifesync-web/src/test/java/ru/zahaand/lifesync/web/notification/
├── HabitReminderSchedulerIT.java               # NEW — integration test
├── GoalNotificationConsumerIT.java             # NEW — integration test
└── TelegramNotificationConsumerIT.java         # NEW — integration test (enhanced message)
```

**Structure Decision**: Follows existing Maven multi-module hexagonal layout. New `notification` packages in domain and infrastructure for reminder-specific ports and implementations. Consumers remain in their existing packages (notification/, goal/). New `notification/` subdirectory under db/changelog for reminder-related migrations.

## Implementation Phases

### Phase 0: Liquibase Migrations
- V18: Create `sent_reminders` table (habit_id UUID FK habits CASCADE, user_id UUID FK users CASCADE, sent_date DATE, created_at TIMESTAMPTZ). Composite unique on (habit_id, sent_date). Indexes on habit_id, user_id, sent_date. Rollback block.
- V19: Create `goal_sent_milestones` table (id UUID PK, goal_id UUID FK goals CASCADE, threshold INT NOT NULL, sent_at TIMESTAMPTZ, created_at TIMESTAMPTZ). Composite unique on (goal_id, threshold). Indexes on goal_id. Rollback block.
- Update db.changelog-master.xml with V18 and V19 includes.
- **Constitution**: Principle V (Liquibase, XML, native tags, rollback, FK CASCADE, indexes)

### Phase 1: Domain Layer — Ports
- SentReminderRepository port interface: `existsByHabitIdAndDate(HabitId, LocalDate)`, `save(HabitId, UUID userId, LocalDate)`
- GoalSentMilestoneRepository port interface: `existsByGoalIdAndThreshold(GoalId, int threshold)`, `save(GoalId, int threshold)`
- Add `findHabitsForReminder(LocalTime reminderTime)` to HabitRepository — returns habits with matching reminder_time, non-deleted, where user has telegramChatId set (requires join). Alternative: add `findAllActiveWithReminderTime()` returning all habits with non-null reminder_time (filter in scheduler).
- **Constitution**: Principle I (no Spring/jOOQ in domain), VII (no Lombok), IX (code style)

### Phase 2: Infrastructure — Repositories
- JooqSentReminderRepository (@Repository, DSLContext): implements SentReminderRepository port
- JooqGoalSentMilestoneRepository (@Repository, DSLContext): implements GoalSentMilestoneRepository port
- Extend JooqHabitRepository with `findAllActiveWithReminderTime()` — SELECT habits WHERE reminder_time IS NOT NULL AND deleted_at IS NULL, joined with users+user_profiles to get telegramChatId and timezone
- **Constitution**: Principle III (userId isolation), V (no DDL)

### Phase 3: Infrastructure — Scheduler & Consumer Updates
- HabitReminderScheduler (@Component, @Scheduled cron="0 * * * * *"):
  - Inject: HabitRepository (or new query method), SentReminderRepository, HabitLogRepository, UserRepository, TelegramNotificationSender, Clock, `@Value("${lifesync.telegram.enabled}") boolean telegramEnabled`
  - Each minute: if telegramEnabled=false, log DEBUG + return
  - Load all active habits with non-null reminder_time via new repo method
  - Group by user → load user profile (timezone, telegramChatId)
  - For each habit: convert current UTC time to user's timezone, compare HH:mm with reminder_time
  - If match AND not already sent today (check sent_reminders) AND habit not completed today (check habit_logs) → send Telegram, insert sent_reminders
  - Non-overlapping: use `@Scheduled(fixedDelay=0, initialDelay=0)` with cron OR rely on single-threaded scheduler default behavior
  - Error handling: catch Telegram failures per-habit, log WARN, continue to next habit
- Update TelegramNotificationConsumer: change message from "You've reached a {N}-day streak! Keep going!" to "🔥 {habitTitle}: {N}-day streak! Keep going!" — habit.getTitle() already loaded
- Update GoalNotificationConsumer: replace stub with real implementation:
  - Inject: GoalRepository, GoalSentMilestoneRepository, UserRepository, TelegramNotificationSender, `@Value("${lifesync.telegram.enabled}") boolean telegramEnabled`
  - On event: check telegramEnabled, load goal via findById(goalId), skip if deleted
  - Determine milestones crossed: for each threshold in [25, 50, 75, 100]: if progressPercentage >= threshold AND !goalSentMilestoneRepository.existsByGoalIdAndThreshold(goalId, threshold) → send notification, save milestone
  - Load user, check telegramChatId
  - Messages: < 100%: "🎯 {goalTitle}: {N}% complete! Keep going!" / 100%: "🎯 {goalTitle}: Goal achieved! Congratulations! 🎉"
- **Constitution**: Principle IV (SRP), VIII (logging), IX (constructor injection)

### Phase 4: Tests
- Unit tests:
  - HabitReminderSchedulerTest: mock all dependencies, test timezone conversion, test skip-when-disabled, test skip-when-completed, test skip-when-already-sent, test send-and-save
  - GoalNotificationConsumerTest: mock dependencies, test milestone detection (25/50/75/100), test skip duplicate, test skip-when-no-telegram, test multi-milestone jump (10→80)
  - TelegramNotificationConsumerTest: verify new message format includes habit title
- Integration tests (all in lifesync-web, extend BaseIT):
  - HabitReminderSchedulerIT: create user with timezone + telegram, create habit with reminder_time, trigger scheduler, verify Telegram called and sent_reminders row created
  - GoalNotificationConsumerIT: publish GoalProgressUpdatedEvent, verify milestone notifications sent, verify dedup, verify multi-milestone
  - TelegramNotificationConsumerIT: publish HabitCompletedEvent for habit at milestone streak, verify message contains habit title
- **Constitution**: Principle X (MockitoExtension, Testcontainers, @Nested, @DisplayName, JaCoCo >= 80%)

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Scheduler location | lifesync-infrastructure | @Scheduled is a Spring concern; domain/application must remain framework-free |
| Reminder query strategy | Load all habits with reminder_time, filter in Java | Simpler than complex SQL per-timezone; habit count is small for B2C single-user |
| Goal milestone dedup | Separate table (goal_sent_milestones) | Persistent across restarts; ProcessedEventRepository only tracks event dedup, not milestone-level dedup |
| Sent reminders tracking | Dedicated table (sent_reminders) | Composite unique (habit_id, sent_date) prevents duplicates at DB level; survives restarts |
| Consumer message format | Emojis in messages | User-facing Telegram messages benefit from visual markers; matches spec exactly |
| No new API endpoints | Backend-only sprint | All existing endpoints (habits CRUD, goals CRUD, PUT /users/me/telegram) already support the needed data; no new user-facing API surface |
| Scheduler non-overlap | Spring single-threaded default | Spring's default `@Scheduled` executor is single-threaded; tasks don't overlap unless explicitly configured otherwise |

## Complexity Tracking

No constitution violations. No complexity overrides needed.
