# Implementation Quality Checklist: Habits Core

**Purpose**: Validate requirements completeness, clarity, and consistency across 7 focus areas before Sprint 4 implementation begins
**Created**: 2026-03-30
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [contracts/habit-endpoints.yaml](../contracts/habit-endpoints.yaml)

## jOOQ Code Generation

- [ ] CHK001 - Is the jOOQ codegen plugin configuration fully specified — JDBC URL, target package, output directory, table inclusion list, Maven profile name? [Completeness, Plan §0.2]
- [ ] CHK002 - Are the requirements for migrating existing `JooqUserRepository` and `JooqRefreshTokenRepository` from string-based DSL to generated classes explicitly scoped, or is "same migration pattern" the only guidance? [Clarity, Plan §0.3]
- [ ] CHK003 - Is the requirement that existing repository tests must pass without modification after migration stated as a measurable acceptance criterion, not just an assumption? [Measurability, Spec §US4-SC2, SC-006]
- [ ] CHK004 - Are the exact 14 tables to be included in code generation enumerated, or is "all tables from public schema" the only specification? [Clarity, Plan §0.2]
- [ ] CHK005 - Is the requirement to commit or not commit generated sources to version control explicitly stated? [Gap, Plan §0.2]
- [ ] CHK006 - Are requirements for handling jOOQ codegen failure scenarios defined — e.g., DB not running, schema out of sync? [Coverage, Gap]
- [ ] CHK007 - Is the `build-helper-maven-plugin` dependency for adding generated sources to the classpath documented in plan requirements? [Completeness, Research §R1]

## Streak Calculation

- [ ] CHK008 - Is the DAILY streak algorithm fully specified — does "consecutive days ending today or yesterday" define whether today with no log yet counts as streak-alive? [Clarity, Spec §FR-012]
- [ ] CHK009 - Is the WEEKLY streak algorithm defined with equal precision to DAILY — what constitutes "consecutive weeks with at least one completion"? Does ISO week numbering vs calendar week matter? [Clarity, Spec §Edge Cases]
- [ ] CHK010 - Is the CUSTOM streak algorithm specified with concrete examples — e.g., if target is Mon/Wed/Fri and user misses Wed, does the streak break? [Clarity, Spec §Edge Cases]
- [ ] CHK011 - Is the requirement for Clock injection stated as a hard constraint with specific prohibition of `Instant.now()` / `LocalDate.now()`? [Measurability, Spec §Assumptions, Constitution §X]
- [ ] CHK012 - Are streak recalculation boundary conditions defined — zero completions, single completion, all completions deleted? [Edge Case Coverage, Spec §Edge Cases]
- [ ] CHK013 - Is the requirement that longestStreak "never decreases unless underlying log data changes" precise enough — does soft-deleting a log count as "data change"? [Ambiguity, Spec §FR-013]
- [ ] CHK014 - Are streak recalculation trigger points exhaustively listed — is it only on log create and log delete, or also on habit frequency change? [Coverage, Gap]
- [ ] CHK015 - Is the performance requirement "accurate within 1 second" (SC-003) consistent with the synchronous-in-same-transaction design, or does it need clarification for large log histories? [Consistency, Spec §SC-003, Research §R3]

## User Data Isolation

- [ ] CHK016 - Does the spec explicitly require userId predicate in ALL repository query methods, including `findLogDatesDesc` used for streak calculation? [Completeness, Spec §FR-006, Plan §1.2]
- [ ] CHK017 - Is the requirement for 404 (not 403) on ownership failure stated consistently across all 9 endpoints in both spec and contract? [Consistency, Spec §FR-006, Contracts]
- [ ] CHK018 - Is the isolation requirement stated for the `deleteHabitLog` endpoint — does it require verifying ownership of both the habit AND the log, or just one? [Clarity, Plan §2.2]
- [ ] CHK019 - Are data isolation requirements defined for the streak endpoint — can a user query streak data for another user's habit? [Coverage, Spec §FR-014]
- [ ] CHK020 - Is the requirement for userId predicate scoped to only user-facing queries, or does it also apply to internal system queries (e.g., admin)? [Clarity, Constitution §III]

## Soft Delete

- [ ] CHK021 - Is the `deleted_at IS NULL` filter requirement explicitly stated for EVERY query method in `HabitRepository` and `HabitLogRepository` port definitions? [Completeness, Plan §1.2, §3.1]
- [ ] CHK022 - Is the requirement that soft-deleted habits exclude their completion logs from user-facing queries explicitly documented, or only implied by the habit-level filter? [Clarity, Spec §FR-005]
- [ ] CHK023 - Are requirements defined for whether soft-deleted logs are included in streak recalculation queries (`findLogDatesDesc`)? [Gap, Critical]
- [ ] CHK024 - Is the behavior when completing a soft-deleted habit specified separately from completing an inactive habit? [Clarity, Spec §Edge Cases, FR-007]
- [ ] CHK025 - Are the `updatedAt` timestamp semantics on soft-delete defined — does `softDelete()` also update `updatedAt`? [Gap]
- [ ] CHK026 - Is the absence of soft-delete on `habit_streaks` an explicit design decision documented in the data model, or just omitted? [Completeness, Data Model]

## API First & Contract Completeness

- [ ] CHK027 - Does the contract define `servers: [{url: /api/v1}]` as required by the migration strategy, or is it only mentioned in the research? [Completeness, Research §R5, Spec §FR-000]
- [ ] CHK028 - Are all 11 existing endpoint paths updated to be relative to `/api/v1` server URL in the contract, or only the 9 new habit endpoints? [Coverage, Spec §FR-000]
- [ ] CHK029 - Is the `CreateHabitRequestDto` validation for CUSTOM frequency + empty `targetDaysOfWeek` expressible in the OpenAPI schema, or does the spec rely on server-side validation only? [Clarity, Spec §FR-016, Contracts §CreateHabitRequestDto]
- [ ] CHK030 - Does the `UpdateHabitRequestDto` schema define behavior for setting `description` or `reminderTime` to null (clearing optional fields) vs omitting them (no change)? [Ambiguity, Contracts §UpdateHabitRequestDto]
- [ ] CHK031 - Is the 409 response for `completeHabit` disambiguated between "duplicate date" and "inactive habit" in the contract, or are both reasons merged into one status code? [Clarity, Contracts §/habits/{id}/complete]
- [ ] CHK032 - Are error response body schemas consistent — does every 4xx/5xx response reference `ErrorResponseDto` uniformly? [Consistency, Contracts]
- [ ] CHK033 - Is the requirement to run `mvn generate-sources` BEFORE controller implementation stated as a sequencing gate, not just a recommendation? [Measurability, Spec §Assumptions, Constitution §II]
- [ ] CHK034 - Is `maxLength` for `note` field in `CompleteHabitRequestDto` defined, or is it unbounded text? [Gap, Contracts §CompleteHabitRequestDto]
- [ ] CHK035 - Is `maxLength` for `description` field in `CreateHabitRequestDto` defined? [Gap, Contracts §CreateHabitRequestDto]

## Hexagonal Architecture

- [ ] CHK036 - Are the module-level dependency boundaries between domain, application, infrastructure, and web explicitly restated in the plan, or assumed from constitution? [Completeness, Constitution §I, Plan §Project Structure]
- [ ] CHK037 - Is the prohibition of jOOQ imports in domain and application modules stated as a verifiable constraint with specific disallowed import patterns? [Measurability, Constitution §I]
- [ ] CHK038 - Is `StreakCalculatorService` placement in `lifesync-application` consistent with Constitution §I, given it contains "pure domain logic"? [Consistency, Plan §2.1, Constitution §I]
- [ ] CHK039 - Are the `HabitPage` and `HabitLogPage` inner records in domain port interfaces free of any framework types (no Spring `Pageable`, no jOOQ `Result`)? [Clarity, Plan §1.2, Research §R4]
- [ ] CHK040 - Is the mapping boundary between DB column `name` and domain field `title` defined as an infrastructure-layer responsibility only, not leaking into domain? [Clarity, Research §R6, Plan §DB-to-API Field Mapping]
- [ ] CHK041 - Are Jackson `ObjectMapper` imports for JSONB mapping restricted to infrastructure module only, given Jackson is prohibited in domain? [Consistency, Constitution §I, Research §R2]
- [ ] CHK042 - Is the `UseCaseConfig` bean wiring in `lifesync-app` documented as the only place where cross-module dependencies are assembled? [Completeness, Plan §3.4]

## Constitution Compliance

- [ ] CHK043 - Is the "no Lombok" constraint stated for ALL new classes — entities, value objects, use cases, repositories, controllers, DTOs — or only inherited from constitution? [Completeness, Constitution §VII]
- [ ] CHK044 - Are all new domain entity constructors specified as explicit (no default constructors), consistent with Constitution §IX? [Consistency, Constitution §IX]
- [ ] CHK045 - Is the "no null from public methods" requirement addressed for all port methods — do `findByIdAndUserId` and similar return `Optional` or throw? [Clarity, Constitution §IX, Plan §1.2]
- [ ] CHK046 - Is the requirement for `@DisplayName` on all test methods and `@Nested` grouping per method documented for the 9+ new test classes? [Coverage, Constitution §X]
- [ ] CHK047 - Is the `@Transactional` placement requirement (only on application-layer use cases) stated for `CompleteHabitUseCase` and `DeleteHabitLogUseCase`? [Consistency, Constitution §Dev Standards 11, Plan §2.2]
- [ ] CHK048 - Are Conventional Commit message formats specified for the Sprint 4 implementation — expected scopes `(habits)`, `(infra)`, `(api)` for different phases? [Completeness, Constitution §Dev Standards 7]
- [ ] CHK049 - Is the logging requirement (DEBUG for params, INFO for business success) specified for all new use cases, or only inherited from constitution? [Coverage, Constitution §VIII]
- [ ] CHK050 - Are Liquibase migration file naming conventions (V15, V16) consistent with the existing sequence, and is there a documented check that no gaps or conflicts exist with other sprint branches? [Consistency, Constitution §V]

## Notes

- Items marked `[Gap]` indicate requirements that may need to be added to the spec or plan before implementation
- Items marked `[Ambiguity]` highlight wording that could lead to divergent implementations
- Items marked `[Critical]` in combination with `[Gap]` should be resolved before task generation
- Cross-references use: `Spec §` for spec.md, `Plan §` for plan.md, `Contracts §` for habit-endpoints.yaml, `Research §` for research.md, `Constitution §` for constitution.md, `Data Model` for data-model.md
