# Architecture Quality Checklist: Reminders & Notifications

**Purpose**: Validate completeness, clarity, and consistency of architectural requirements across scheduler design, idempotency mechanisms, consumer implementations, and constitution compliance
**Created**: 2026-03-31
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [data-model.md](../data-model.md)

## HabitReminderScheduler — Timezone & Scheduling Requirements

- [ ] CHK001 - Is the scheduler frequency explicitly defined with a specific cron expression or interval value? [Clarity, Spec §FR-001]
- [ ] CHK002 - Is the timezone conversion algorithm specified — which library/method converts UTC to user's local time, and is the comparison granularity (minute-level) documented? [Clarity, Spec §FR-013]
- [ ] CHK003 - Is the `telegram.enabled` guard behavior explicitly specified — does the scheduler skip the entire run or skip individual sends, and what log level? [Clarity, Spec §FR-005]
- [ ] CHK004 - Are requirements defined for what happens when `reminder_time` matches but the user's `telegramChatId` is null or blank? [Completeness, Spec §FR-002]
- [ ] CHK005 - Is the non-overlapping execution requirement specified with a concrete mechanism (single-threaded default, fixedDelay, or explicit lock)? [Clarity, Spec §FR-015]
- [ ] CHK006 - Are requirements defined for habits whose `reminder_time` falls on the boundary of a DST transition (e.g., 02:30 during spring-forward)? [Edge Case, Spec §Edge Cases]
- [ ] CHK007 - Is the behavior specified when the scheduler run takes longer than 60 seconds — are reminders for the skipped minute lost or queued? [Edge Case, Spec §Edge Cases]
- [ ] CHK008 - Is the `reminder_time` matching logic explicitly defined — does it compare HH:mm equality or use a time range/window? [Clarity, Data Model §Habit Reminder Flow]
- [ ] CHK009 - Are requirements defined for the completion check — is it specified that the habit must be checked for today's completion (in user's local date, not UTC date) before sending? [Completeness, Spec §FR-003]
- [ ] CHK010 - Is the query strategy documented — does the scheduler load all habits in one query or per-user, and is the join to user_profiles (for timezone + telegramChatId) specified? [Clarity, Plan §Phase 2]

## sent_reminders — Idempotency Requirements

- [ ] CHK011 - Is the composite unique constraint on `(habit_id, sent_date)` explicitly documented as the dedup mechanism? [Completeness, Data Model §sent_reminders]
- [ ] CHK012 - Is the `sent_date` field defined as the user's local date (not UTC date) — and is this distinction explicitly documented? [Clarity, Spec §FR-004]
- [ ] CHK013 - Is the check-then-insert sequence specified — does the spec require checking `existsByHabitIdAndDate` before sending, or rely solely on the unique constraint? [Clarity, Data Model §Habit Reminder Flow]
- [ ] CHK014 - Are requirements defined for behavior when the app restarts mid-day — is it explicitly stated that existing `sent_reminders` rows prevent re-sending? [Completeness, Spec §Edge Cases]
- [ ] CHK015 - Is the `user_id` column's purpose clarified — is it for data integrity/FK only, or also used in queries? [Clarity, Data Model §sent_reminders]
- [ ] CHK016 - Are cleanup/retention requirements defined for old `sent_reminders` rows (e.g., delete after N days)? [Gap]

## goal_sent_milestones — Milestone Idempotency Requirements

- [ ] CHK017 - Is the distinction between event-level idempotency (ProcessedEventRepository) and milestone-level idempotency (goal_sent_milestones) explicitly documented? [Clarity, Plan §Key Design Decisions]
- [ ] CHK018 - Is the composite unique constraint on `(goal_id, threshold)` explicitly specified as the dedup mechanism? [Completeness, Data Model §goal_sent_milestones]
- [ ] CHK019 - Are requirements defined for the multi-milestone scenario — when progress jumps from 10% to 80%, is the iteration order over thresholds [25, 50, 75] specified? [Completeness, Spec §FR-008]
- [ ] CHK020 - Is the behavior explicitly defined when progress decreases below a previously notified threshold and then re-crosses it — does the spec clearly state no re-notification? [Clarity, Spec §FR-009]
- [ ] CHK021 - Are requirements defined for what happens when Telegram send succeeds for 25% milestone but fails for 50% — is 25% still recorded, and is 50% retried on next event? [Edge Case, Gap]
- [ ] CHK022 - Is the threshold set explicitly defined as exactly {25, 50, 75, 100} — could future thresholds be added, and is extensibility addressed? [Completeness, Spec §FR-007]
- [ ] CHK023 - Are cleanup requirements defined for goal_sent_milestones rows when a goal is soft-deleted? [Gap]

## TelegramNotificationConsumer — Message Enhancement Requirements

- [ ] CHK024 - Is the new message format explicitly defined with exact template including emoji placement? [Clarity, Spec §FR-012]
- [ ] CHK025 - Is it specified that the habit title is already available from the loaded Habit entity (no additional query needed)? [Completeness, Plan §Phase 3]
- [ ] CHK026 - Are requirements defined for what the message should contain when the habit title contains special characters (e.g., Telegram Markdown conflicts)? [Edge Case, Gap]
- [ ] CHK027 - Is it explicitly stated that the existing streak detection logic (milestone set: 7/14/21/30/60/90) is NOT modified — only the message template changes? [Clarity, Spec §US-3]
- [ ] CHK028 - Is backward compatibility addressed — are there requirements for messages already in transit (events received during deployment) during the format change? [Edge Case, Gap]

## GoalNotificationConsumer — Real Implementation Requirements

- [ ] CHK029 - Are all four milestone thresholds (25%, 50%, 75%, 100%) explicitly enumerated in requirements? [Completeness, Spec §FR-007]
- [ ] CHK030 - Is the special message format for 100% completion explicitly distinguished from the standard format? [Clarity, Spec §FR-007]
- [ ] CHK031 - Is it specified which dependencies must be injected — GoalRepository, GoalSentMilestoneRepository, UserRepository, TelegramNotificationSender, telegramEnabled flag? [Completeness, Plan §Phase 3]
- [ ] CHK032 - Is the `telegram.enabled` check placement specified — should it happen before or after the ProcessedEventRepository idempotency check? [Clarity, Data Model §Goal Milestone Flow]
- [ ] CHK033 - Are requirements defined for the order of operations: idempotency check → telegram guard → goal load → user load → milestone check → send → save milestone → save processed event? [Clarity, Data Model §Goal Milestone Flow]
- [ ] CHK034 - Is the behavior specified when the goal is soft-deleted at the time the event is processed? [Completeness, Spec §FR-018]
- [ ] CHK035 - Is the behavior specified when the GoalProgressUpdatedEvent's `progressPercentage` is exactly on a threshold (e.g., exactly 25) — is `>=` the comparison operator? [Clarity, Gap]
- [ ] CHK036 - Are requirements defined for what `GoalProgressUpdatedEvent.habitId` being null means for the consumer (manual progress update)? [Completeness, Spec §Clarifications]

## Constitution Compliance

- [ ] CHK037 - Are domain port interfaces (SentReminderRepository, GoalSentMilestoneRepository) explicitly required to be free of Spring, jOOQ, Kafka, or Jackson imports? [Consistency, Constitution §I]
- [ ] CHK038 - Is it specified that HabitReminderScheduler must reside in the infrastructure module (not domain or application)? [Consistency, Constitution §I, Plan §Phase 3]
- [ ] CHK039 - Is constructor injection explicitly required for all new classes — HabitReminderScheduler, JooqSentReminderRepository, JooqGoalSentMilestoneRepository? [Consistency, Constitution §IX]
- [ ] CHK040 - Is it specified that no Lombok annotations are used in any new or modified file? [Consistency, Constitution §VII]
- [ ] CHK041 - Are logging standards specified — Logger via LoggerFactory, DEBUG for params/steps, INFO for success, WARN for Telegram failures, ERROR for DB failures? [Consistency, Constitution §VIII]
- [ ] CHK042 - Is the member order requirement applied — static fields (Logger, constants) → instance fields → constructors → methods? [Consistency, Constitution §IX]
- [ ] CHK043 - Are commit granularity requirements defined — one commit per phase or logical group? [Consistency, Constitution §Dev Standards 7]

## Liquibase Migrations V18/V19 Requirements

- [ ] CHK044 - Is the migration file structure specified per Constitution §V — createTable → addForeignKeyConstraint → addUniqueConstraint → createIndex → rollback? [Completeness, Constitution §V]
- [ ] CHK045 - Are FK constraints explicitly required with `onDelete="CASCADE"` for both sent_reminders (habit_id→habits, user_id→users) and goal_sent_milestones (goal_id→goals)? [Completeness, Data Model]
- [ ] CHK046 - Are rollback blocks explicitly required — `<dropAllForeignKeyConstraints>` then `<dropTable>` for both V18 and V19? [Completeness, Constitution §V]
- [ ] CHK047 - Is the UUID PK column type specified as `type="uuid" defaultValueComputed="gen_random_uuid()"`? [Consistency, Constitution §V]
- [ ] CHK048 - Are timestamp columns specified as `type="timestamptz"` (not plain `timestamp`)? [Consistency, Constitution §V]
- [ ] CHK049 - Is the changelog master file (db.changelog-master.xml) update requirement documented — new includes for V18 and V19 in correct order? [Completeness, Plan §Phase 0]
- [ ] CHK050 - Is the directory placement for V18/V19 specified — under `db/changelog/notification/` per plan, consistent with existing domain-based organization? [Consistency, Plan §Project Structure]
- [ ] CHK051 - Is it specified that XML comments inside `<changeSet>` blocks are PROHIBITED per Constitution §V? [Consistency, Constitution §V]

## Cross-Cutting Consistency

- [ ] CHK052 - Are the `sent_date` (DATE type in sent_reminders) and `sent_at` (TIMESTAMPTZ in goal_sent_milestones) type choices consistent with their semantics — date for daily dedup, timestamp for audit? [Consistency, Data Model]
- [ ] CHK053 - Is the error handling strategy consistent across scheduler and consumers — log WARN for Telegram failures, continue processing, no exception propagation? [Consistency, Spec §FR-016]
- [ ] CHK054 - Are message formats consistent in emoji usage — "⏰" for reminders, "🔥" for streaks, "🎯" for goals — and is each format documented in a single authoritative location? [Consistency, Spec §Key Entities]
- [ ] CHK055 - Is the `telegramChatId` null check consistently required across all three notification paths (scheduler, streak consumer, goal consumer)? [Consistency, Spec §FR-002/FR-017]

## Notes

- Check items off as completed: `[x]`
- Items referencing `[Gap]` indicate requirements that may need to be added to spec or plan
- Items referencing `[Consistency]` flag potential conflicts between artifacts
- Cross-reference: `Spec §FR-NNN` = spec.md Functional Requirement, `Spec §US-N` = User Story N, `Constitution §X` = Constitution Principle
