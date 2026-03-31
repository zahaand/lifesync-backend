# Architecture Quality Checklist: Goals Feature

**Purpose**: Validate completeness, clarity, and consistency of architectural requirements across progress calculation, event-driven integration, data isolation, and constitution compliance
**Created**: 2026-03-31
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [data-model.md](../data-model.md)

## Progress Calculation — Dual Path Requirements

- [ ] CHK001 - Is the automatic progress formula explicitly defined with all variables (numerator, denominator, rounding)? [Clarity, Spec §Clarification 3]
- [ ] CHK002 - Is the meaning of "completed habit" precisely specified — what date, what table condition (`habit_logs` row existence for `logDate` where `deleted_at IS NULL`)? [Clarity, Gap]
- [ ] CHK003 - Is the `logDate` source for automatic progress recalculation explicitly documented — is it the `HabitCompletedEvent.logDate` field? [Clarity, Spec §US-5]
- [ ] CHK004 - Are the requirements for manual-then-automatic override behavior unambiguous — does the spec clearly state which value wins and when? [Clarity, Spec §US-4 Scenario 5, Edge Cases]
- [ ] CHK005 - Is the boundary behavior specified for division-by-zero — what is progress when `countTotalByGoalId` returns 0 (all habits unlinked)? [Edge Case, Gap]
- [ ] CHK006 - Are progress boundary values (exactly 0, exactly 100) consistently defined as triggering status transitions across both manual and automatic paths? [Consistency, Spec §FR-016]
- [ ] CHK007 - Is the rounding behavior specified — does "rounded to integer" mean `Math.round`, truncation, or ceiling? [Clarity, Spec §Clarification 3]
- [ ] CHK008 - Are requirements defined for what happens when a habit log is **deleted** (`DeleteHabitLogUseCase`) for a habit linked to a goal — should progress be recalculated downward? [Coverage, Gap]
- [ ] CHK009 - Is the event payload for manual vs automatic progress updates consistently specified — does `GoalProgressUpdatedEvent.habitId` have a defined value (or null) for manual updates? [Consistency, Spec §FR-017]

## Habit-Goal Many-to-Many Relationship

- [ ] CHK010 - Are ownership validation requirements defined for both sides of the link — must the user own BOTH the goal AND the habit? [Completeness, Spec §FR-011, US-3 Scenario 4]
- [ ] CHK011 - Is the duplicate link rejection behavior specified with the exact error type (409 Conflict)? [Clarity, Spec §FR-012]
- [ ] CHK012 - Are cascade behavior requirements explicitly documented — what happens to `goal_habits` rows when a goal is soft-deleted vs when a habit is soft-deleted? [Completeness, Spec §Edge Cases]
- [ ] CHK013 - Is the distinction between soft-delete cascade (goal milestones) and hard-delete cascade (goal-habit links via DB FK) clearly stated in requirements? [Clarity, Spec §Edge Cases]
- [ ] CHK014 - Are requirements defined for unlinking behavior — does unlinking trigger progress recalculation, and if so, is the formula reapplied immediately? [Coverage, Spec §US-3 Scenario 3]
- [ ] CHK015 - Is the behavior specified when the last habit is unlinked from a goal — does progress retain its current value or reset? [Edge Case, Spec §Edge Cases]
- [ ] CHK016 - Are requirements defined for linking a habit that was already completed today — should linking retroactively affect progress? [Edge Case, Gap]

## GoalProgressConsumer — Idempotency & Multi-Goal Recalculation

- [ ] CHK017 - Is the idempotency mechanism explicitly specified — `ProcessedEventRepository` with `eventId + consumerGroup` deduplication? [Completeness, Spec §US-5 Scenario 4]
- [ ] CHK018 - Are requirements defined for recalculating ALL active goals linked to a completed habit, not just one? [Completeness, Spec §FR-018]
- [ ] CHK019 - Is the consumer group name specified or is naming left to implementation? [Clarity, Gap]
- [ ] CHK020 - Are requirements for skipped (soft-deleted) goals in the recalculation loop explicitly documented? [Coverage, Spec §US-5 Scenario 5]
- [ ] CHK021 - Is the failure handling specified — if recalculation succeeds for goal A but fails for goal B, is the entire event retried or only goal B? [Edge Case, Gap]
- [ ] CHK022 - Are the DLQ routing requirements consistent with existing consumer patterns (3 retries, exponential backoff)? [Consistency, Spec §FR-021]
- [ ] CHK023 - Is the ordering of operations documented — should idempotency record be saved BEFORE or AFTER all goals are recalculated? [Clarity, Gap]

## GoalProgressUpdatedEvent — Publishing & Fire-and-Forget

- [ ] CHK024 - Is the `AFTER_COMMIT` publishing requirement explicitly stated for both manual and automatic progress paths? [Completeness, Plan §Key Design Decisions]
- [ ] CHK025 - Is the fire-and-forget behavior specified — if event publishing fails, should the progress update still be considered successful? [Clarity, Spec §Assumptions]
- [ ] CHK026 - Are the event payload fields fully specified for both paths — `eventId`, `goalId`, `userId`, `habitId` (nullable for manual?), `progressPercentage`, `occurredAt`? [Completeness, Data Model §GoalProgressUpdatedEvent]
- [ ] CHK027 - Is it specified that manual progress updates must publish one event per update, while automatic updates publish one event per affected goal? [Consistency, Spec §FR-017 vs FR-018]
- [ ] CHK028 - Are requirements defined for the partition key in Kafka messages — is it `goalId` for both manual and automatic paths? [Clarity, Gap]

## GoalAnalyticsConsumer & GoalNotificationConsumer Stubs

- [ ] CHK029 - Are the stub consumer requirements specific enough — do they define exact log level (INFO), log content (goal ID + progress), and idempotency behavior? [Completeness, Spec §US-6]
- [ ] CHK030 - Is the scope boundary between Sprint 6 (stubs) and Sprint 7 (real notifications with 25/50/75/100% thresholds) clearly documented? [Completeness, Spec §Clarification 2]
- [ ] CHK031 - Are consumer group names for both stubs specified or is naming left to implementation? [Clarity, Gap]
- [ ] CHK032 - Is it specified that both stubs share the same DLQ configuration as existing consumers? [Consistency, Plan §Phase 5]

## User Data Isolation

- [ ] CHK033 - Are userId predicate requirements explicitly stated for ALL goal repository query methods (find, list, update, delete)? [Completeness, Spec §FR-006]
- [ ] CHK034 - Are userId predicate requirements defined for milestone repository queries — does `findByIdAndGoalId` also require userId, or is ownership inherited from the goal? [Clarity, Data Model §GoalMilestoneRepository]
- [ ] CHK035 - Are userId predicate requirements defined for goal-habit link queries — particularly `findActiveGoalIdsByHabitId` used by the consumer? [Coverage, Data Model §GoalHabitLinkRepository]
- [ ] CHK036 - Is the ownership validation flow specified for nested resources — e.g., when deleting a milestone, must the spec require validating both goal ownership AND milestone-belongs-to-goal? [Completeness, Gap]
- [ ] CHK037 - Are the error responses for ownership violations consistently specified — 404 (not found) vs 403 (forbidden)? [Consistency, Spec §US-1 Scenario 5]
- [ ] CHK038 - Is the userId source for consumer-initiated operations specified — does the consumer use `HabitCompletedEvent.userId` and must it match the goal's userId? [Clarity, Gap]

## API First — YAML Before Implementation

- [ ] CHK039 - Does the API contract in `contracts/goals-api.yaml` define all 12 endpoints listed in the plan? [Completeness, Plan §Phase 1]
- [ ] CHK040 - Are all request/response DTOs defined with field descriptions on every schema field per Principle XII? [Completeness, Constitution §XII]
- [ ] CHK041 - Is JSON Merge Patch semantics for PATCH endpoints (updateGoal, updateMilestone) explicitly documented in the contract? [Clarity, Contract §GoalUpdateRequestDto]
- [ ] CHK042 - Are pagination parameters (page, size) and response format (GoalPageResponseDto) consistent with existing habit endpoint patterns? [Consistency, Contract §GoalPageResponseDto]
- [ ] CHK043 - Is the `PATCH /goals/{id}/progress` endpoint clearly distinguished from `PATCH /goals/{id}` — are their responsibilities non-overlapping? [Clarity, Contract]

## Constitution Compliance

- [ ] CHK044 - Are the domain entity requirements explicitly free of Spring, jOOQ, Kafka, or Jackson dependencies (Principle I)? [Consistency, Constitution §I]
- [ ] CHK045 - Are use case requirements free of jOOQ, Kafka, or Spring MVC imports — only Spring `@Transactional` and `ApplicationEventPublisher` permitted (Principle I)? [Consistency, Constitution §I]
- [ ] CHK046 - Are constructor injection requirements specified for all new classes — no `@Autowired` on fields (Principle IX)? [Completeness, Constitution §IX]
- [ ] CHK047 - Are the 3 new domain exceptions mapped to HTTP status codes in the requirements — GoalNotFoundException (404), GoalHabitLinkNotFoundException (404), DuplicateGoalHabitLinkException (409)? [Completeness, Plan §Phase 6]
- [ ] CHK048 - Are OpenAPI `description` blocks specified with business rules AND how-to-test instructions for every endpoint (Principle XII)? [Completeness, Constitution §XII]
- [ ] CHK049 - Are named request body `examples` with real data specified for endpoints with request bodies (Principle XII)? [Completeness, Constitution §XII]
- [ ] CHK050 - Is the `UseCaseConfig` bean wiring approach (manual `new`, no component scanning) documented as a requirement for all 12 use cases? [Completeness, Plan §Phase 6]
- [ ] CHK051 - Are commit granularity requirements defined — one commit per phase, not one monolithic sprint commit (Constitution §Dev Standards 7)? [Completeness, Constitution §Dev Standards]

## Cross-Cutting Consistency

- [ ] CHK052 - Are the `GoalStatus` enum values (ACTIVE, COMPLETED) consistent between the spec, data model, API contract, and DB schema default? [Consistency, Spec §FR-016 vs DB §V7]
- [ ] CHK053 - Is the progress field range (0-100) consistently specified across spec requirements, API validation, and domain entity constraints? [Consistency, Spec §FR-015 vs Contract §GoalProgressUpdateRequestDto]
- [ ] CHK054 - Are soft-delete requirements consistent — goals and milestones use `deleted_at`, but `goal_habits` uses hard delete via FK cascade? [Consistency, Data Model]
- [ ] CHK055 - Is the naming convention for consumers consistent with Constitution §Dev Standards 3 (`{Purpose}Consumer`)? [Consistency, Constitution §Dev Standards]

## Notes

- Check items off as completed: `[x]`
- Items referencing `[Gap]` indicate requirements that may need to be added to spec or plan
- Items referencing `[Consistency]` flag potential conflicts between artifacts
- Cross-reference: `Spec §FR-NNN` = spec.md Functional Requirement, `Spec §US-N` = User Story N, `Constitution §X` = Constitution Principle
