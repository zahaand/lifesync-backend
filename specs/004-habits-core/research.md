# Research: Habits Core

**Date**: 2026-03-30
**Feature**: 004-habits-core

## R1: jOOQ Code Generation Configuration

**Decision**: Use `jooq-codegen-maven-plugin` with Maven profile `jooq-codegen` bound to `generate-sources` phase. Generate classes on-demand (not every build) since it requires a live database.

**Rationale**: jOOQ codegen connects to a running PostgreSQL to introspect the schema. Binding to a profile avoids CI failures when no DB is available. Generated sources are committed to `target/generated-sources/jooq/` and added to the compile classpath via `build-helper-maven-plugin`.

**Alternatives considered**:
- Testcontainers-based codegen (jooq-codegen-maven + testcontainers JDBC URL): more portable but slower and adds Testcontainers as a build dependency
- DDL-based codegen (jOOQ's DDLDatabase): doesn't support Liquibase XML directly, requires SQL scripts
- Gradle jOOQ plugin: not applicable (Maven project)

## R2: JSONB Mapping for target_days_of_week

**Decision**: Store `target_days_of_week` as JSONB array of day name strings (e.g., `["MONDAY","WEDNESDAY","FRIDAY"]`). Map in jOOQ repository using `JSONB.valueOf()` for writes and Jackson `ObjectMapper` for reads.

**Rationale**: JSONB is PostgreSQL-native, supports indexing if needed later, and maps naturally to `Set<DayOfWeek>` via `DayOfWeek.valueOf()`. Array of strings is human-readable in DB.

**Alternatives considered**:
- Integer bitmask: compact but not human-readable in DB, harder to query
- Separate junction table (habit_target_days): normalized but adds complexity for a simple list of max 7 values
- PostgreSQL array type: less flexible than JSONB for future extensions

## R3: Streak Calculation Strategy

**Decision**: Synchronous recalculation in the same transaction as completion log/delete. Query all non-deleted log dates for the habit, compute streak in memory, upsert streak record.

**Rationale**: Streaks depend on completion data that just changed — async would create a window of stale data. The log dates query is bounded (one habit's logs) and indexed by habit_id. SC-003 requires accuracy within 1 second, which synchronous satisfies. Kafka events are Sprint 5 scope.

**Alternatives considered**:
- Async recalculation via Kafka event: adds latency, stale window, Kafka is Sprint 5
- Incremental update (just increment/decrement): breaks on deletion of non-latest log, requires complex edge case handling
- Materialized view: PostgreSQL-specific, harder to test, refresh timing issues

## R4: Pagination Strategy

**Decision**: Use query parameters (page, size) mapped to jOOQ `LIMIT`/`OFFSET` with a separate `COUNT(*)` query. Return page metadata in response DTOs matching existing `UserPageResponseDto` pattern.

**Rationale**: Consistent with existing `GetAdminUsersUseCase` and `UserPage` pattern. Offset-based pagination is simpler and sufficient for the expected data volume (< 1000 habits per user). Max page size capped at 100.

**Alternatives considered**:
- Cursor-based pagination (keyset): better for large datasets but adds complexity, not needed at current scale
- Spring Data Pageable: violates constitution (no Spring Data JPA), and domain ports should not depend on Spring types

## R5: /api/v1 Prefix Migration Strategy

**Decision**: Add `servers: [{url: /api/v1}]` to OpenAPI YAML. All paths in YAML become relative to this server URL. The openapi-generator will produce interfaces with `/api/v1` in `@RequestMapping` annotations. SecurityConfig rules updated to match new paths.

**Rationale**: Server URL approach is the standard OpenAPI 3.1 mechanism. Generated interfaces automatically include the prefix — no manual path manipulation needed. All 20 endpoints (11 existing + 9 new) share the same base.

**Alternatives considered**:
- `server.servlet.context-path=/api/v1` in application.yml: affects ALL endpoints including actuator and swagger, harder to exclude
- Manual prefix in each path: duplicative, error-prone
- Spring `@RequestMapping("/api/v1")` on controller classes: violates API First (hand-written mappings prohibited)

## R6: DB Column "name" ↔ API "title" Mapping

**Decision**: Map at the infrastructure layer (JooqHabitRepository). The domain entity uses `title` as the field name. The jOOQ repository reads from `HABITS.NAME` and writes to it, but maps to/from `Habit.title` in the `mapToHabit()` / insert/update methods.

**Rationale**: The API and domain use "title" which better describes user-facing semantics. The DB column "name" was defined in Sprint 1. Renaming the DB column would require modifying applied changeset V4 (constitution violation V.4). Mapping at the repo layer is the cleanest boundary.

**Alternatives considered**:
- New migration to rename column: risky, modifying applied changeset is prohibited
- New migration to add `title` column + copy data + drop `name`: adds unnecessary complexity and migration risk for a cosmetic change
- Use "name" everywhere: less intuitive for API consumers
