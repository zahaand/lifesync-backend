# Implementation Plan: Habits Core

**Branch**: `004-habits-core` | **Date**: 2026-03-30 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-habits-core/spec.md`

## Summary

Implement core habit tracking functionality: CRUD for habits, completion logging with soft-delete, streak calculation (DAILY/WEEKLY/CUSTOM), and jOOQ code generation infrastructure. Also includes prerequisite work: migrating all existing endpoints to `/api/v1` prefix and replacing string-based jOOQ DSL with generated type-safe classes.

## Technical Context

**Language/Version**: Java 21 LTS
**Primary Dependencies**: Spring Boot 3.5.x, jOOQ 3.19, Liquibase 4.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0
**Storage**: PostgreSQL 16 (via Docker Compose)
**Testing**: JUnit 5 + AssertJ + Mockito + Testcontainers (PostgreSQL 16-alpine)
**Target Platform**: Linux server (Docker)
**Project Type**: web-service (REST API)
**Performance Goals**: < 2s for habit operations, streak recalculation < 1s (synchronous, same transaction)
**Constraints**: No Lombok, no Hibernate, no Spring Data JPA, constructor injection only, all fields final
**Scale/Scope**: 100+ habits per user, single-digit millisecond DB queries with userId-indexed tables

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | Domain (entities, ports) → Application (use cases) → Infrastructure (jOOQ repos) → Web (controllers). No layer violations. |
| II | API First | PASS | OpenAPI YAML updated before controller implementation. Generated interfaces via openapi-generator-maven-plugin. |
| III | User Data Isolation | PASS | All jOOQ queries include userId predicate. UseCases validate ownership. 404 on access denied (no info leakage). |
| IV | Single Responsibility | PASS | One UseCase per operation. StreakCalculatorService handles streak logic only. Repos have no business logic. |
| V | Liquibase Migrations | PASS | V15 (target_days_of_week JSONB) + V16 (reminder_time TIME) via XML with rollback blocks. Native tags only. |
| VI | Secrets via Environment Variables | PASS | No new secrets introduced. DB credentials remain in env vars. |
| VII | Portfolio Readability | PASS | No Lombok. No speculative features. All identifiers English. |
| VIII | Logging Standards | PASS | Logger via LoggerFactory. DEBUG for params, INFO for business success, WARN for skips, ERROR for failures. |
| IX | Code Style | PASS | All fields final, constructor injection, curly braces always, explicit constructors. |
| X | Testing Standards | PASS | Unit tests with Mockito, ITs with Testcontainers BaseIT, Clock.fixed() for streaks, JaCoCo ≥ 80%. |
| XI | Code/Doc Language | PASS | English code/identifiers, Russian commit bodies, Conventional Commits. |

All gates pass. No constitution violations.

## Project Structure

### Documentation (this feature)

```text
specs/004-habits-core/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Merged into lifesync-api.yaml (single source of truth)
├── checklists/          # Specification quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
lifesync-api-spec/
└── src/main/resources/openapi/
    └── lifesync-api.yaml              # Updated: /api/v1 prefix + 9 habit endpoints

lifesync-domain/
└── src/main/java/ru/zahaand/lifesync/domain/habit/
    ├── Habit.java                     # Immutable entity
    ├── HabitId.java                   # UUID value object (record)
    ├── HabitLog.java                  # Immutable entity
    ├── HabitLogId.java                # UUID value object (record)
    ├── HabitStreak.java               # Value object (record)
    ├── Frequency.java                 # Enum: DAILY, WEEKLY, CUSTOM
    ├── DayOfWeekSet.java              # Value object wrapping Set<DayOfWeek>
    ├── HabitRepository.java           # Port interface
    ├── HabitLogRepository.java        # Port interface
    ├── HabitStreakRepository.java      # Port interface
    └── exception/
        ├── HabitNotFoundException.java
        ├── HabitInactiveException.java
        └── DuplicateHabitLogException.java

lifesync-application/
└── src/main/java/ru/zahaand/lifesync/application/habit/
    ├── CreateHabitUseCase.java
    ├── GetHabitsUseCase.java
    ├── GetHabitUseCase.java
    ├── UpdateHabitUseCase.java
    ├── DeleteHabitUseCase.java
    ├── CompleteHabitUseCase.java       # Logs completion + recalculates streak
    ├── DeleteHabitLogUseCase.java      # Soft-deletes log + recalculates streak
    ├── GetHabitLogsUseCase.java
    ├── GetHabitStreakUseCase.java
    └── StreakCalculatorService.java    # Application service, injected Clock

lifesync-infrastructure/
└── src/main/java/ru/zahaand/lifesync/infrastructure/habit/
    ├── JooqHabitRepository.java       # Generated jOOQ classes
    ├── JooqHabitLogRepository.java    # Generated jOOQ classes
    └── JooqHabitStreakRepository.java  # Generated jOOQ classes
└── src/main/resources/db/changelog/habit/
    ├── V4__create_habits.xml          # Existing
    ├── V5__create_habit_logs.xml      # Existing
    ├── V6__create_habit_streaks.xml   # Existing
    ├── V15__add_target_days_of_week_to_habits.xml  # New: JSONB column
    └── V16__add_reminder_time_to_habits.xml        # New: TIME column

lifesync-web/
└── src/main/java/ru/zahaand/lifesync/web/habit/
    └── HabitController.java           # Implements generated HabitApi interface

lifesync-app/
└── src/main/java/ru/zahaand/lifesync/app/config/
    ├── UseCaseConfig.java             # Updated: 9 new habit use case beans
    └── SecurityConfig.java            # Updated: /api/v1 prefix rules
```

**Structure Decision**: Follows existing hexagonal module layout. New `habit` package added under each module's domain root, mirroring the existing `user` package structure.

## Implementation Phases

### Phase 0: Prerequisites & Infrastructure

**0.1 — Migrate all endpoints to /api/v1 prefix**

Files to change:
- `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml` — add `servers: [{url: /api/v1}]`, remove `/auth`, `/users`, `/admin` prefixes from all paths (they become relative to server URL)
- `lifesync-app/.../SecurityConfig.java` — update `requestMatchers` from `/auth/**` to `/api/v1/auth/**`, `/admin/**` to `/api/v1/admin/**`, `/users/**` pattern
- `lifesync-web/src/test/` — update all test URLs in integration tests
- Run `mvn generate-sources` to regenerate interfaces with new paths
- Verify all existing tests pass

**0.2 — Configure jooq-codegen-maven-plugin**

Add to `lifesync-infrastructure/pom.xml`:
- Plugin: `org.jooq:jooq-codegen-maven-plugin:3.19.x`
- JDBC: `jdbc:postgresql://localhost:5432/lifesync` (credentials from env/defaults)
- Target: `target/generated-sources/jooq/`
- Package: `ru.zahaand.lifesync.infrastructure.generated`
- Include all tables from `public` schema
- Maven profile `jooq-codegen` to run on-demand (not every build)

**0.3 — Migrate existing repositories to generated jOOQ classes**

- `JooqUserRepository.java` — replace `DSL.table("users")` / `DSL.field("users.id", UUID.class)` with generated `Tables.USERS` / `USERS.ID`
- `JooqRefreshTokenRepository.java` — same migration pattern
- Remove all static `Table<?>` / `Field<?>` string-based declarations
- Run existing tests — must pass without modification

### Phase 1: Domain & Data Model

**1.1 — Liquibase migrations**

- `V15__add_target_days_of_week_to_habits.xml`:
  - `<addColumn tableName="habits">` with `<column name="target_days_of_week" type="jsonb"/>`
  - `<rollback><dropColumn .../></rollback>`

- `V16__add_reminder_time_to_habits.xml`:
  - `<addColumn tableName="habits">` with `<column name="reminder_time" type="time"/>`
  - `<rollback><dropColumn .../></rollback>`

**1.2 — Domain entities & ports (lifesync-domain)**

- `Frequency` enum: `DAILY`, `WEEKLY`, `CUSTOM`
- `HabitId` record: `UUID value` (with null check)
- `HabitLogId` record: `UUID value` (with null check)
- `DayOfWeekSet` record: wraps `Set<DayOfWeek>`, immutable, validation (non-empty for CUSTOM)
- `Habit` immutable class: id, userId, title, description, frequency, targetDaysOfWeek (DayOfWeekSet, nullable), reminderTime (LocalTime, nullable), active, createdAt, updatedAt, deletedAt. Methods: `softDelete(Instant)`, `withUpdatedAt(Instant)`, `update(...)`, `isDeleted()`, `isActive()`
- `HabitLog` immutable class: id, habitId, userId, logDate, note, createdAt, updatedAt, deletedAt. Methods: `softDelete(Instant)`, `isDeleted()`
- `HabitStreak` record: habitId, currentStreak, longestStreak, lastLogDate
- `HabitRepository` port: `save(Habit)`, `findByIdAndUserId(HabitId, UserId)`, `findAllByUserId(UserId, String status, int page, int size)`, `update(Habit)`. Inner record `HabitPage` for pagination.
- `HabitLogRepository` port: `save(HabitLog)`, `findByIdAndUserId(HabitLogId, UserId)`, `findByHabitIdAndUserId(HabitId, UserId, int page, int size)`, `findByHabitIdAndLogDateAndUserId(HabitId, LocalDate, UserId)`, `update(HabitLog)`, `findLogDatesDesc(HabitId, UserId)`. Inner record `HabitLogPage`. NOTE: All methods include UserId per Constitution §III.
- `HabitStreakRepository` port: `findByHabitIdAndUserId(HabitId, UserId)`, `save(HabitStreak)`, `update(HabitStreak)`. NOTE: findByHabitId includes UserId per Constitution §III.
- Domain exceptions: `HabitNotFoundException`, `HabitInactiveException`, `DuplicateHabitLogException`

**1.3 — OpenAPI YAML (9 habit endpoints)**

Single source of truth: `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`.
Tag: `Habit` — all endpoints require `bearerAuth` security.
All paths relative to `/api/v1` server URL.

| Path | Method | OperationId | Request | Response | Codes |
|------|--------|-------------|---------|----------|-------|
| /habits | GET | getHabits | query: status, page, size | HabitPageResponseDto | 200, 401 |
| /habits | POST | createHabit | CreateHabitRequestDto | HabitResponseDto | 201, 400, 401 |
| /habits/{id} | GET | getHabit | - | HabitResponseDto | 200, 401, 404 |
| /habits/{id} | PATCH | updateHabit | UpdateHabitRequestDto | HabitResponseDto | 200, 400, 401, 404 |
| /habits/{id} | DELETE | deleteHabit | - | - | 204, 401, 404 |
| /habits/{id}/complete | POST | completeHabit | CompleteHabitRequestDto | HabitLogResponseDto | 201, 400, 401, 404, 409 |
| /habits/{id}/complete/{logId} | DELETE | deleteHabitLog | - | - | 204, 401, 404 |
| /habits/{id}/logs | GET | getHabitLogs | query: page, size | HabitLogPageResponseDto | 200, 401, 404 |
| /habits/{id}/streak | GET | getHabitStreak | - | HabitStreakResponseDto | 200, 401, 404 |

New DTOs:
- `CreateHabitRequestDto`: title (required, max 200), description, frequency (enum), targetDaysOfWeek (array of enum), reminderTime (string, HH:mm format)
- `UpdateHabitRequestDto`: title, description, frequency, targetDaysOfWeek, reminderTime, isActive (all optional, minProperties: 1)
- `CompleteHabitRequestDto`: date (required, format: date), note (optional)
- `HabitResponseDto`: id, title, description, frequency, targetDaysOfWeek, reminderTime, isActive, createdAt
- `HabitLogResponseDto`: id, habitId, date, note, createdAt
- `HabitStreakResponseDto`: currentStreak, longestStreak, lastLogDate
- `HabitPageResponseDto`: content (array), totalElements, totalPages, page, size
- `HabitLogPageResponseDto`: content (array), totalElements, totalPages, page, size
- `DayOfWeek` enum: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY

### Phase 2: Application Logic

**2.1 — StreakCalculatorService (lifesync-application)**

Application service with injected `Clock`. Placed in lifesync-application (not domain) because it requires Clock injection — a Spring bean:
- `HabitStreak calculate(Frequency frequency, DayOfWeekSet targetDays, List<LocalDate> logDatesDesc)`
- DAILY: walk backwards from today/yesterday counting consecutive days
- WEEKLY: group by ISO week, count consecutive weeks with >= 1 log
- CUSTOM: walk backwards through target days only, count consecutive completed target days
- Returns `HabitStreak` with currentStreak, longestStreak, lastLogDate

**2.2 — Use cases (lifesync-application)**

All use cases follow existing pattern: plain classes instantiated via `UseCaseConfig` beans.

- `CreateHabitUseCase(HabitRepository, HabitStreakRepository, Clock)`: validate frequency/targetDays consistency, save habit, initialize streak record (0/0/null)
- `GetHabitsUseCase(HabitRepository)`: delegate to repo with userId, status filter, pagination
- `GetHabitUseCase(HabitRepository)`: findByIdAndUserId, throw HabitNotFoundException if absent
- `UpdateHabitUseCase(HabitRepository, Clock)`: find, verify ownership, apply updates, save. Validate frequency/targetDays consistency on change.
- `DeleteHabitUseCase(HabitRepository, Clock)`: find, verify ownership, soft-delete
- `CompleteHabitUseCase(HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock)`: @Transactional. Find habit → verify active → check duplicate date → save log → recalculate streak → save streak
- `DeleteHabitLogUseCase(HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock)`: @Transactional. Find log → verify ownership via habit → soft-delete log → recalculate streak → save streak
- `GetHabitLogsUseCase(HabitRepository, HabitLogRepository)`: verify habit ownership, then delegate paginated query
- `GetHabitStreakUseCase(HabitRepository, HabitStreakRepository)`: verify habit ownership, return streak data

### Phase 3: Infrastructure & Web

**3.1 — jOOQ repositories (lifesync-infrastructure)**

All using generated jOOQ classes from `ru.zahaand.lifesync.infrastructure.generated.tables.*`.

- `JooqHabitRepository`: CRUD on `HABITS` table, LEFT JOIN none needed. Filter `deleted_at IS NULL`, `user_id = ?`. Pagination with `LIMIT`/`OFFSET` + count query. Map `name` column ↔ domain `title`. JSONB column `target_days_of_week` mapped via `JSONB.valueOf()` / Jackson ObjectMapper.
- `JooqHabitLogRepository`: CRUD on `HABIT_LOGS` table. Filter `deleted_at IS NULL`. Unique check via `findByHabitIdAndLogDate`. Pagination for log listing. `findLogDatesDesc` returns ordered dates for streak calculation.
- `JooqHabitStreakRepository`: CRUD on `HABIT_STREAKS` table. One-to-one with habit via unique `habit_id`.

**3.2 — HabitController (lifesync-web)**

Implements generated `HabitApi` interface. Gets `UserId` from `SecurityContextHolder` (same pattern as `UserController`). Delegates to use cases. Maps domain objects → generated DTOs.

**3.3 — GlobalExceptionHandler updates**

Add handlers:
- `HabitNotFoundException` → 404 NOT_FOUND
- `HabitInactiveException` → 409 CONFLICT (attempt to complete inactive habit)
- `DuplicateHabitLogException` → 409 CONFLICT (same habit + date)

**3.4 — UseCaseConfig updates (lifesync-app)**

Add 9 new `@Bean` methods for habit use cases + 1 for `StreakCalculatorService`. Wire repositories, Clock, and service dependencies.

### Phase 4: Testing

**4.1 — Unit tests (lifesync-application)**

- `CreateHabitUseCaseTest`: happy path, CUSTOM without days → validation error, duplicate title (if applicable)
- `CompleteHabitUseCaseTest`: happy path + streak update, inactive habit → reject, duplicate date → reject, ownership check
- `DeleteHabitLogUseCaseTest`: happy path + streak recalc, not found
- `StreakCalculatorServiceTest`: DAILY streak (consecutive, broken, yesterday-alive), WEEKLY streak (consecutive weeks), CUSTOM streak (Mon/Wed/Fri pattern), zero completions, all with `Clock.fixed()`
- `GetHabitsUseCaseTest`, `GetHabitUseCaseTest`, `UpdateHabitUseCaseTest`, `DeleteHabitUseCaseTest`, `GetHabitLogsUseCaseTest`, `GetHabitStreakUseCaseTest`

**4.2 — Integration tests (lifesync-web)**

- `HabitControllerIT extends BaseIT`: full CRUD flow, completion flow, streak flow, ownership isolation (two users), pagination, error responses (404, 409), soft-delete behavior
- Update existing `AuthControllerIT` / `UserControllerIT` / `AdminControllerIT` for `/api/v1` prefix

**4.3 — JaCoCo**

- Ensure domain + application modules maintain ≥ 80% line coverage
- Add `jacoco-maven-plugin` to `lifesync-domain/pom.xml` (currently only on application)

## DB-to-API Field Mapping

| DB Column (habits) | Domain Field | API Field (DTO) |
|---------------------|-------------|-----------------|
| name | title | title |
| description | description | description |
| frequency | frequency | frequency |
| target_days_of_week | targetDaysOfWeek | targetDaysOfWeek |
| reminder_time | reminderTime | reminderTime |
| active | active | isActive |
| created_at | createdAt | createdAt |
| updated_at | updatedAt | (not exposed) |
| deleted_at | deletedAt | (not exposed) |

## Complexity Tracking

No constitution violations. No complexity exceptions needed.
