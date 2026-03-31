# Research: Goals Feature

**Branch**: `006-goals-feature` | **Date**: 2026-03-31

## R-001: Progress Calculation Semantics

**Decision**: Goal progress = (count of linked habits with a `habit_logs` entry for the event's `logDate` where `deleted_at IS NULL`) / (total linked habits count) * 100, rounded to nearest integer.

**Rationale**: `habit_logs` has no `completed` boolean — row existence for `(habit_id, log_date)` IS the completion signal. The `HabitCompletedEvent.logDate` field determines which date to evaluate. Progress reflects the most recent recalculation's date context.

**Alternatives considered**:
- Cumulative all-time completion: rejected — progress would never decrease, not useful for daily habit tracking.
- Today-only calculation: rejected — events can carry non-today logDates (retroactive logging).

## R-002: Existing Database Schema Sufficiency

**Decision**: No new Liquibase migrations needed. V7 (goals), V8 (goal_milestones), V9 (goal_habits) cover all required columns and constraints.

**Rationale**: 
- `goals.progress` (integer, default 0) supports both manual and automatic progress.
- `goals.status` (varchar(20), default 'ACTIVE') supports ACTIVE/COMPLETED transitions.
- `goal_habits` junction table with unique constraint on `(goal_id, habit_id)` prevents duplicates.
- `goal_milestones` has `sort_order`, `completed`, `completed_at`, `deleted_at` — all needed for milestone management.

**Alternatives considered**: Adding a `progress_source` column (MANUAL/AUTO) to goals — rejected as YAGNI. The spec states manual values are overwritten by the next automatic recalculation.

## R-003: GoalHabitLinkRepository Cross-Domain Query

**Decision**: Add `countCompletedByGoalIdAndDate(GoalId, LocalDate)` and `countTotalByGoalId(GoalId)` methods to `GoalHabitLinkRepository` domain port. Infrastructure implementation joins `goal_habits` with `habit_logs`.

**Rationale**: The port interface declares the contract (what data the application needs); the infrastructure implements it (how to get it, including cross-table joins). This keeps the domain clean while allowing efficient single-query counting.

**Alternatives considered**:
- Separate `HabitCompletionChecker` port: rejected — adds unnecessary abstraction for two counting methods.
- Having the use case call HabitLogRepository directly: rejected — mixes habit and goal domain concerns in the application layer.

## R-004: Consumer Architecture for GoalProgressConsumer

**Decision**: `GoalProgressConsumer` listens to `habit.log.completed` topic (same as existing StreakCalculatorConsumer) and delegates to `RecalculateGoalProgressUseCase`. The use case publishes `GoalProgressUpdatedEvent` via `ApplicationEventPublisher`, which is picked up by the existing `KafkaGoalEventPublisher` after commit.

**Rationale**: Follows the exact pattern of `StreakCalculatorConsumer` → streak calculation. Reuses existing `KafkaGoalEventPublisher` (already implemented, handles `GoalProgressUpdatedEvent`). Maintains hexagonal architecture: consumer (infra) → use case (app) → event publisher (infra via Spring event bus).

**Alternatives considered**:
- Consumer directly publishing to Kafka: rejected — violates hexagonal architecture, bypasses use case layer.
- Synchronous progress update in `CompleteHabitUseCase`: rejected — couples habit and goal domains.

## R-005: Manual Progress Event Publishing

**Decision**: `UpdateGoalProgressUseCase` (manual PATCH) publishes `GoalProgressUpdatedEvent` via `ApplicationEventPublisher` inside a `@Transactional` method. The existing `KafkaGoalEventPublisher` picks it up via `@TransactionalEventListener(phase = AFTER_COMMIT)`.

**Rationale**: Both manual and automatic paths use the same event publishing mechanism, ensuring consistent downstream processing by GoalAnalyticsConsumer and GoalNotificationConsumer.

**Alternatives considered**: Not publishing events for manual updates — rejected. The spec (FR-017) requires events for both paths.

## R-006: GetGoal Enriched Response

**Decision**: `GetGoalUseCase` returns the goal entity plus milestones list and linked habit IDs. The controller maps linked habit IDs into the response DTO. Full habit details are not included — the frontend fetches them separately if needed.

**Rationale**: FR-003 requires "full details including milestones and linked habits." Returning habit IDs keeps the query simple and avoids cross-domain entity loading in the use case. The goal detail response includes `linkedHabitIds: [UUID]`.

**Alternatives considered**: Including full habit objects (id, name) — rejected for now. Would require HabitRepository dependency in GetGoalUseCase, coupling goal and habit domains unnecessarily.

## R-007: DomainEvent Sealed Interface

**Decision**: `GoalProgressUpdatedEvent` is already in the `permits` clause of the sealed `DomainEvent` interface. No change needed.

**Rationale**: Verified in `DomainEvent.java`: `permits HabitCompletedEvent, GoalProgressUpdatedEvent`.

## R-008: SecurityConfig — No Changes Needed

**Decision**: No changes to `SecurityConfig`. Goals endpoints fall under `anyRequest().authenticated()`.

**Rationale**: All `/api/v1/goals/**` paths are automatically protected by the catch-all authenticated rule. No admin-specific or public goal endpoints exist.
