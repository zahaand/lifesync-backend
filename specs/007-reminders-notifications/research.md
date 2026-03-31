# Research: Reminders & Notifications

**Branch**: `007-reminders-notifications` | **Date**: 2026-03-31

## Decision 1: Scheduler Mechanism

**Decision**: Spring `@Scheduled(cron = "0 * * * * *")` in `lifesync-infrastructure` module.

**Rationale**: Spring's built-in scheduler is sufficient for a single-instance B2C app. Every minute, the job queries all habits with `reminder_time IS NOT NULL`, converts UTC to each user's timezone, and matches against the current minute. Single-threaded default ensures non-overlapping execution.

**Alternatives considered**:
- Quartz Scheduler: Overkill for a single-instance app. Adds dependency and complexity.
- Kafka-based reminders (publish events with delayed delivery): Kafka doesn't natively support delayed messages. Would require a custom scheduler anyway.
- Database-polling with `@Scheduled(fixedDelay)`: Similar to chosen approach but cron expression is more expressive and aligns with the "every minute" requirement.

## Decision 2: Reminder Duplicate Prevention

**Decision**: `sent_reminders` table with composite unique constraint on `(habit_id, sent_date)`.

**Rationale**: DB-level unique constraint guarantees no duplicates even under race conditions or app restarts. The scheduler checks `existsByHabitIdAndDate` before sending and inserts after success. The unique constraint is the safety net.

**Alternatives considered**:
- In-memory set (HashMap): Lost on restart. Duplicates after app restart within same day.
- ProcessedEventRepository: Designed for Kafka event dedup (eventId + consumerGroup), not for scheduled job dedup by date.
- Redis with TTL: Adds infrastructure dependency for a simple dedup that a DB table handles perfectly.

## Decision 3: Goal Milestone Dedup Strategy

**Decision**: `goal_sent_milestones` table with composite unique constraint on `(goal_id, threshold)`.

**Rationale**: Goal milestones (25/50/75/100%) must never be re-sent even if progress regresses and re-crosses a threshold. This requires persistent per-goal-per-threshold tracking that survives restarts. A dedicated table is simpler than overloading ProcessedEventRepository (which tracks event-level, not milestone-level dedup).

**Alternatives considered**:
- Track in ProcessedEventRepository: Would need synthetic event IDs like `{goalId}-milestone-{threshold}`. Conflates event dedup with business logic dedup.
- Store milestones as a JSON array on the goal entity: Couples notification concern to goal domain entity. Violates SRP.
- In-memory cache: Lost on restart. Duplicates.

## Decision 4: Reminder Query Strategy

**Decision**: Load all active habits with non-null `reminder_time` in a single query, filter by timezone match in Java.

**Rationale**: For a single-user B2C app, the total number of habits with reminders is small (tens, not thousands). Loading all and filtering in Java is simpler than building complex per-timezone SQL queries. The query joins habits → users → user_profiles to get timezone and telegramChatId in one round trip.

**Alternatives considered**:
- SQL-level timezone filtering (WHERE reminder_time = current_time AT TIME ZONE user.timezone): Complex, database-specific, harder to test.
- Per-user queries: N+1 problem. Worse than a single bulk load for small datasets.

## Decision 5: Scheduler Placement (Architecture)

**Decision**: `HabitReminderScheduler` in `lifesync-infrastructure` module, package `ru.zahaand.lifesync.infrastructure.notification`.

**Rationale**: `@Scheduled` is a Spring framework concern. Constitution Principle I prohibits Spring annotations in domain and application layers. The scheduler is an adapter that orchestrates infrastructure (Telegram sending) using domain ports (repositories). This follows the same pattern as Kafka consumers being in infrastructure.

**Alternatives considered**:
- Application layer use case called by scheduler: Would add unnecessary indirection. The scheduler IS the adapter — it doesn't need a use case wrapper since there's no business logic beyond "find habits, check time, send message".
- Web layer: Incorrect — no HTTP involvement.

## Decision 6: Existing Code Changes

**Decision**: Modify TelegramNotificationConsumer message (1 line), replace GoalNotificationConsumer stub body (full rewrite preserving class skeleton).

**Rationale**:
- TelegramNotificationConsumer already loads `Habit` entity and has `habit.getTitle()` accessible. Change is minimal: update the message string template.
- GoalNotificationConsumer is a pure stub (just logs). Full rewrite adds: GoalRepository, GoalSentMilestoneRepository, UserRepository, TelegramNotificationSender injection. Milestone threshold logic. Telegram sending.

**Alternatives considered**:
- Create new consumers instead of modifying stubs: Would require renaming/removing old stubs, changing consumer groups. Unnecessary churn when the stubs were designed to be replaced.

## Decision 7: No New Kafka Topics

**Decision**: Reuse existing topics. No new topics for reminders.

**Rationale**: Habit reminders are scheduler-driven (not event-driven). The scheduler directly sends Telegram messages — no intermediate Kafka topic needed. Goal and streak notifications already flow through `goal.progress.updated` and `habit.log.completed` respectively.

**Alternatives considered**:
- Publish `HabitReminderDueEvent` to a new Kafka topic, consume in a separate consumer: Adds unnecessary async hop for a synchronous operation. The scheduler already knows exactly which reminders to send.
