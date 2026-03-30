# Tasks: Habits Core

**Input**: Design documents from `/specs/004-habits-core/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, checklists/implementation-quality.md

**Organization**: Tasks are grouped by phase following strict dependency ordering: Prerequisites → Domain → Shared Services → US1 → US2 → US3 → Infrastructure → Web → Tests → Polish.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)

## User Story Mapping

- **US1**: Create and Manage Habits (P1) — CRUD, pagination, soft-delete
- **US2**: Log Habit Completions (P1) — completion log, duplicate check, soft-delete
- **US3**: Track Habit Streaks (P2) — streak calculation, recalculation on log changes
- **US4**: Configure jOOQ Code Generation (P1) — codegen plugin, migrate existing repos

---

## Phase 1: Prerequisites — /api/v1 Migration (FR-000)

**Purpose**: Migrate all existing endpoints to `/api/v1` prefix before any new habit code

- [X] T001 [US4] Add `servers: [{url: /api/v1}]` to OpenAPI YAML and remove path prefixes (`/auth`, `/users`, `/admin`) so all paths become relative to server URL in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [X] T002 [US4] Run `mvn generate-sources -pl lifesync-api-spec` to regenerate controller interfaces with `/api/v1` prefix
- [X] T003 [US4] Update SecurityConfig permit rules from `/auth/**` to `/api/v1/auth/**`, `/admin/**` to `/api/v1/admin/**`, and user path patterns in `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/SecurityConfig.java`
- [X] T004 [US4] Update test URLs in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/user/AuthControllerIT.java` to use `/api/v1/auth/**` paths
- [X] T005 [P] [US4] Update test URLs in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/user/UserControllerIT.java` to use `/api/v1/users/**` paths
- [X] T006 [P] [US4] Update test URLs in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/user/AdminControllerIT.java` to use `/api/v1/admin/**` paths
- [X] T007 [US4] Verify `mvn clean verify` passes — all existing tests green with new `/api/v1` paths

**Checkpoint**: All 11 existing endpoints respond under `/api/v1` prefix. All existing tests pass.

---

## Phase 2: Prerequisites — jOOQ Code Generation (US4)

**Purpose**: Configure jooq-codegen-maven-plugin and migrate existing repos to generated classes

- [X] T008 [US4] Configure `jooq-codegen-maven-plugin` in `lifesync-infrastructure/pom.xml`: Maven profile `jooq-codegen`, JDBC `jdbc:postgresql://localhost:5432/lifesync`, target package `ru.zahaand.lifesync.infrastructure.generated`, output to `src/main/generated-jooq/`, include all tables from `public` schema
- [X] T009 [US4] Add `build-helper-maven-plugin` to `lifesync-infrastructure/pom.xml` to add `src/main/generated-jooq/` to compile classpath
- [X] T010 [US4] Run `mvn generate-sources -P jooq-codegen -pl lifesync-infrastructure` with Docker Compose running — verify generated classes exist for all tables (users, user_profiles, refresh_tokens, habits, habit_logs, habit_streaks, etc.)
- [X] T011 [US4] Fix `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/JooqUserRepository.java` — replace string-based DSL (`DSL.table("users")`, `DSL.field(...)`) with generated `Tables.USERS`, `USERS.ID`, etc.
- [X] T012 [P] [US4] Fix `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/JooqRefreshTokenRepository.java` — replace string-based DSL with generated `Tables.REFRESH_TOKENS`, `REFRESH_TOKENS.TOKEN`, etc.
- [X] T013 [US4] Verify `mvn clean verify` passes — all existing repository tests pass without modification after migration to generated classes

**Checkpoint**: jOOQ codegen configured and working. Existing repos use generated classes. All tests green. US4 complete.

---

## Phase 3: Prerequisites — Liquibase Migrations

**Purpose**: Add new columns before any habit repository code touches them

- [X] T014 [P] Create Liquibase migration `lifesync-infrastructure/src/main/resources/db/changelog/habit/V15__add_target_days_of_week_to_habits.xml` — `<addColumn tableName="habits">` with `<column name="target_days_of_week" type="jsonb"/>` nullable, `<rollback><dropColumn .../></rollback>`
- [X] T015 [P] Create Liquibase migration `lifesync-infrastructure/src/main/resources/db/changelog/habit/V16__add_reminder_time_to_habits.xml` — `<addColumn tableName="habits">` with `<column name="reminder_time" type="time"/>` nullable, `<rollback><dropColumn .../></rollback>`
- [X] T016 Add `<include file="db/changelog/habit/V15__add_target_days_of_week_to_habits.xml"/>` and `<include file="db/changelog/habit/V16__add_reminder_time_to_habits.xml"/>` to `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml`. Verify migrations apply cleanly with Docker Compose running.
- [X] T017 Run `mvn generate-sources -P jooq-codegen -pl lifesync-infrastructure` to regenerate jOOQ classes after V15/V16 migrations. Verify `target_days_of_week` and `reminder_time` columns appear in generated `Tables.HABITS` class.

**Checkpoint**: Database schema has all columns needed for habits domain. Generated jOOQ classes include new columns.

---

## Phase 4: Domain Layer (US1, US2, US3 foundations)

**Purpose**: Domain entities, value objects, ports, and exceptions — pure Java, no framework imports

**⚠️ NOTE**: Phase 4 has NO dependencies on Phases 1-3 — domain is pure Java with zero dependency on DB or jOOQ. Can start immediately in parallel with Phases 1-3.

### Value Objects & Enums

- [X] T018 [P] Create `Frequency` enum (DAILY, WEEKLY, CUSTOM) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/Frequency.java`
- [X] T019 [P] Create `HabitId` record (UUID value, null-check) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitId.java`
- [X] T020 [P] Create `HabitLogId` record (UUID value, null-check) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitLogId.java`
- [X] T021 [P] Create `DayOfWeekSet` record (wraps unmodifiable `Set<DayOfWeek>`, validation: non-empty) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/DayOfWeekSet.java`

### Domain Entities

- [X] T022 Create `Habit` immutable entity in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/Habit.java` — all fields final, explicit constructor, methods: `softDelete(Instant)`, `update(...)` returning new instance, `isDeleted()`, `isActive()`. Field `title` maps to DB column `name`. See data-model.md for full field list.
- [X] T023 Create `HabitLog` immutable entity in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitLog.java` — all fields final, explicit constructor, methods: `softDelete(Instant)`, `isDeleted()`. See data-model.md for full field list.
- [X] T024 Create `HabitStreak` record in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitStreak.java` — pure value object with NO id field (id is managed internally by JooqHabitStreakRepository). Fields: habitId (HabitId), currentStreak (int), longestStreak (int), lastLogDate (LocalDate nullable via Optional getter).

### Domain Exceptions

- [X] T025 [P] Create `HabitNotFoundException` in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/exception/HabitNotFoundException.java`
- [X] T026 [P] Create `HabitInactiveException` in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/exception/HabitInactiveException.java`
- [X] T027 [P] Create `DuplicateHabitLogException` in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/exception/DuplicateHabitLogException.java`

### Repository Ports

- [X] T028 Create `HabitRepository` port interface in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitRepository.java` — methods: `save(Habit)`, `findByIdAndUserId(HabitId, UUID userId)` returning `Optional<Habit>`, `findAllByUserId(UUID userId, String status, int page, int size)` returning `HabitPage`, `update(Habit)`. Inner record `HabitPage(List<Habit> content, long totalElements, int totalPages, int page, int size)`.
- [X] T029 Create `HabitLogRepository` port interface in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitLogRepository.java` — methods: `save(HabitLog)`, `findByIdAndUserId(HabitLogId, UUID userId)` returning `Optional<HabitLog>`, `findByHabitIdAndUserId(HabitId, UUID userId, int page, int size)` returning `HabitLogPage`, `findByHabitIdAndLogDateAndUserId(HabitId, LocalDate, UUID userId)` returning `Optional<HabitLog>`, `update(HabitLog)`, `findLogDatesDesc(HabitId, UUID userId)` returning `List<LocalDate>`. Inner record `HabitLogPage(...)`. NOTE: All methods include UUID userId parameter per Constitution §III.
- [X] T030 Create `HabitStreakRepository` port interface in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitStreakRepository.java` — methods: `findByHabitIdAndUserId(HabitId, UUID userId)` returning `Optional<HabitStreak>`, `save(HabitStreak)`, `update(HabitStreak)`. NOTE: findByHabitId includes userId per Constitution §III.

**Checkpoint**: Domain layer compiles independently. `mvn compile -pl lifesync-domain` passes. No Spring/jOOQ/Jackson imports anywhere in domain module.

---

## Phase 5: Shared Application Services — StreakCalculatorService

**Purpose**: StreakCalculatorService is a shared dependency for US1 (UpdateHabitUseCase), US2 (CompleteHabitUseCase, DeleteHabitLogUseCase), and US3 (GetHabitStreakUseCase). MUST be implemented before any use case that triggers streak recalculation.

- [X] T031 [US3] Implement `StreakCalculatorService` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/StreakCalculatorService.java` — application service with injected Clock (NO @Transactional). Method: `calculate(Frequency, DayOfWeekSet, List<LocalDate> logDatesDesc)` returning HabitStreak. DAILY: walk backwards from today/yesterday counting consecutive days. WEEKLY: group by ISO week, count consecutive weeks with ≥1 log. CUSTOM: walk backwards through target days only, count consecutive completed target days. Compute both currentStreak and longestStreak in single pass through all dates.
- [X] T032 [US3] Create `StreakCalculatorServiceTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/StreakCalculatorServiceTest.java` — extensive tests with Clock.fixed(): DAILY consecutive (3 days → streak 3), DAILY broken (gap → streak 0), DAILY yesterday-alive (last log yesterday → streak alive), WEEKLY consecutive (3 weeks → streak 3), WEEKLY with gap, CUSTOM Mon/Wed/Fri pattern (all hit → streak, miss Wed → broken), zero completions → 0/0, single completion, longestStreak preserved after reset. @Nested per method, @DisplayName in English, @ExtendWith(MockitoExtension.class).

**Checkpoint**: StreakCalculatorService compiles and all tests pass. `mvn test -pl lifesync-application -Dtest="StreakCalculatorServiceTest"`. Thorough coverage for all frequency types.

---

## Phase 6: Application Layer — User Story 1 (Create and Manage Habits, P1) 🎯 MVP

**Goal**: Users can create, list, view, update, and soft-delete habits

**Independent Test**: Create a habit, list it, update it, soft-delete it — all via use case unit tests

### Use Cases

- [x] T033 [US1] Implement `CreateHabitUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/CreateHabitUseCase.java` — validate frequency/targetDays consistency (CUSTOM requires non-empty DayOfWeekSet), save habit, initialize streak record (0/0/null). Dependencies: HabitRepository, HabitStreakRepository, Clock.
- [x] T034 [US1] Implement `GetHabitsUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/GetHabitsUseCase.java` — delegate to HabitRepository with userId, status filter, pagination. Dependency: HabitRepository.
- [x] T035 [US1] Implement `GetHabitUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/GetHabitUseCase.java` — findByIdAndUserId, throw HabitNotFoundException if absent. Dependency: HabitRepository.
- [x] T036 [US1] Implement `UpdateHabitUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/UpdateHabitUseCase.java` — find habit, verify ownership (404 if not found), apply JSON Merge Patch semantics (null clears, absent = no change), validate frequency/targetDays on change, if frequency changed recalculate streak from scratch via StreakCalculatorService. Dependencies: HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock. @Transactional.
- [x] T037 [US1] Implement `DeleteHabitUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/DeleteHabitUseCase.java` — find habit, verify ownership, soft-delete. Dependencies: HabitRepository, Clock.

### Unit Tests for US1

- [x] T038 [P] [US1] Create `CreateHabitUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/CreateHabitUseCaseTest.java` — test: happy path DAILY, happy path CUSTOM with days, CUSTOM without days → validation error, streak record initialized. @ExtendWith(MockitoExtension.class), Clock.fixed().
- [x] T039 [P] [US1] Create `GetHabitsUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/GetHabitsUseCaseTest.java` — test: returns page, filters by status, passes userId.
- [x] T040 [P] [US1] Create `GetHabitUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/GetHabitUseCaseTest.java` — test: found returns habit, not found throws HabitNotFoundException.
- [x] T041 [P] [US1] Create `UpdateHabitUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/UpdateHabitUseCaseTest.java` — test: update title, update frequency triggers streak recalc, PATCH null clears description, not found throws 404, CUSTOM without days → error.
- [x] T042 [P] [US1] Create `DeleteHabitUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/DeleteHabitUseCaseTest.java` — test: happy path soft-delete, not found throws 404.

**Checkpoint**: US1 use cases compile and all unit tests pass. `mvn test -pl lifesync-application -Dtest="*Habit*UseCase*"`.

---

## Phase 7: Application Layer — User Story 2 (Log Habit Completions, P1)

**Goal**: Users can log completions, view log history, and remove log entries

**Independent Test**: Create a habit, log completion, list logs, delete log entry — all via use case unit tests

### Use Cases

- [x] T043 [US2] Implement `CompleteHabitUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/CompleteHabitUseCase.java` — @Transactional. Find habit → verify active (HabitInactiveException if inactive) → check duplicate date (DuplicateHabitLogException) → save log → recalculate streak via StreakCalculatorService → save streak. Dependencies: HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock.
- [x] T044 [US2] Implement `DeleteHabitLogUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/DeleteHabitLogUseCase.java` — @Transactional. Find log → verify ownership via habit userId → soft-delete log → recalculate streak via StreakCalculatorService → save streak. Dependencies: HabitRepository, HabitLogRepository, HabitStreakRepository, StreakCalculatorService, Clock.
- [x] T045 [US2] Implement `GetHabitLogsUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/GetHabitLogsUseCase.java` — verify habit ownership first, then delegate paginated query. Dependencies: HabitRepository, HabitLogRepository.

### Unit Tests for US2

- [x] T046 [P] [US2] Create `CompleteHabitUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/CompleteHabitUseCaseTest.java` — test: happy path + streak updated, inactive habit → HabitInactiveException, duplicate date → DuplicateHabitLogException, soft-deleted habit → HabitNotFoundException (filtered by deleted_at IS NULL in findByIdAndUserId), ownership check (habit not found → 404). Clock.fixed().
- [x] T047 [P] [US2] Create `DeleteHabitLogUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/DeleteHabitLogUseCaseTest.java` — test: happy path + streak recalculated, log not found → 404, ownership verified via habit.
- [x] T048 [P] [US2] Create `GetHabitLogsUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/GetHabitLogsUseCaseTest.java` — test: returns page, verifies habit ownership first.

**Checkpoint**: US2 use cases compile and all unit tests pass.

---

## Phase 8: Application Layer — User Story 3 (Get Habit Streak, P2)

**Goal**: Users can query streak data for any of their habits

**Independent Test**: Create a habit, query streak endpoint — returns 0/0/null for new habit

### Use Case

- [x] T049 [US3] Implement `GetHabitStreakUseCase` in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/habit/GetHabitStreakUseCase.java` — verify habit ownership, return streak data. Dependencies: HabitRepository, HabitStreakRepository.
- [x] T050 [P] [US3] Create `GetHabitStreakUseCaseTest` in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/habit/GetHabitStreakUseCaseTest.java` — test: returns streak, habit not found → 404.

**Checkpoint**: All application layer code compiles and tests pass. `mvn test -pl lifesync-application`.

---

## Phase 9: Infrastructure Layer (jOOQ Repositories)

**Purpose**: Implement repositories using generated jOOQ classes. All queries include userId predicate and `deleted_at IS NULL` filter.

- [x] T051 [P] [US1] Implement `JooqHabitRepository` in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/JooqHabitRepository.java` — using generated `Tables.HABITS`, `HABITS.USER_ID`, etc. Map DB `name` column ↔ domain `title`. JSONB column `target_days_of_week` via `JSONB.valueOf()` for writes / Jackson ObjectMapper for reads. Filter `deleted_at IS NULL` and `user_id = ?` in ALL queries. Pagination: `LIMIT`/`OFFSET` + `COUNT(*)` query. Implements `HabitRepository` port.
- [x] T052 [P] [US2] Implement `JooqHabitLogRepository` in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/JooqHabitLogRepository.java` — filter `deleted_at IS NULL` and `user_id = ?` in ALL queries. `findByHabitIdAndLogDateAndUserId` for duplicate check. `findLogDatesDesc(HabitId, UUID userId)` returns only non-deleted log dates for streak calculation. Pagination for log listing. Implements `HabitLogRepository` port.
- [x] T053 [P] [US3] Implement `JooqHabitStreakRepository` in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/JooqHabitStreakRepository.java` — `findByHabitIdAndUserId` includes userId via JOIN on habits table. Upsert pattern: save for new, update for existing. Maps domain HabitStreak VO (no id) to DB row (with id). One-to-one with habit via unique habit_id. Implements `HabitStreakRepository` port.

**Checkpoint**: Infrastructure layer compiles. `mvn compile -pl lifesync-infrastructure`.

---

## Phase 10: Web Layer

**Purpose**: Controller, exception handlers, bean wiring. OpenAPI YAML already includes habit endpoints — generate interfaces first.

- [x] T054 Run `mvn generate-sources -pl lifesync-api-spec` to generate `HabitApi` interface from updated OpenAPI YAML with 9 habit endpoints
- [x] T055 Implement `HabitController` in `lifesync-web/src/main/java/ru/zahaand/lifesync/web/habit/HabitController.java` — implements generated `HabitApi` interface. Extract userId from `SecurityContextHolder` (same pattern as UserController). Delegate to use cases. Map domain objects → generated response DTOs. Map request DTOs → domain parameters.
- [x] T056 Add exception handlers to `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/GlobalExceptionHandler.java` — `HabitNotFoundException` → 404, `HabitInactiveException` → 409, `DuplicateHabitLogException` → 409
- [x] T057 Add 9 habit use case `@Bean` methods + 1 `StreakCalculatorService` bean to `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/UseCaseConfig.java` — wire repositories, Clock, and StreakCalculatorService dependencies
- [x] T058 Update SecurityConfig in `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/SecurityConfig.java` — add `/api/v1/habits/**` to authenticated endpoint rules (require bearerAuth)

**Checkpoint**: Application starts with `mvn spring-boot:run -pl lifesync-app -Dspring-boot.run.profiles=dev`. All endpoints respond (with Docker Compose running). Swagger UI shows all 20 endpoints.

---

## Phase 11: Integration Tests

**Purpose**: Full-stack tests with Testcontainers PostgreSQL, extending existing BaseIT

- [x] T059 Create `HabitControllerIT` in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/habit/HabitControllerIT.java` extending `BaseIT` — test all 9 endpoints: create habit (201), list habits with pagination and status filter (200), get habit by id (200), update habit with PATCH semantics (200), delete habit soft-delete (204), complete habit (201), duplicate completion (409), complete inactive habit (409), delete log (204), get logs paginated (200), get streak (200), ownership isolation (two users — 404 on cross-access), not found (404)

**Checkpoint**: All integration tests pass. `mvn verify -pl lifesync-web`.

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: Coverage verification, final validations

- [x] T060 Verify JaCoCo ≥ 80% on lifesync-domain and lifesync-application: `mvn verify` and check `target/site/jacoco/index.html`
- [x] T061 Run full build: `mvn clean verify` — all tests pass, coverage thresholds met, no compilation warnings
- [x] T062 Verify no constitution violations: no Lombok imports, no Hibernate/Spring Data JPA, all fields final, constructor injection only, no `Instant.now()` or `LocalDate.now()` calls, no jOOQ imports in domain/application modules

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (API v1 Migration): No dependencies — start immediately
- **Phase 2** (jOOQ Codegen): Depends on Phase 1 completion (OpenAPI YAML regeneration)
- **Phase 3** (Migrations): Can start after Phase 2 (needs running DB + jOOQ codegen for regen)
- **Phase 4** (Domain): **NO dependencies** — can start immediately in parallel with Phases 1-3. Domain is pure Java with zero dependency on DB or jOOQ.
- **Phase 5** (StreakCalculatorService): Depends on Phase 4 (domain entities). Shared prerequisite for US1/US2/US3.
- **Phase 6** (US1 Application): Depends on Phase 4 + Phase 5 (domain + StreakCalculatorService for UpdateHabitUseCase)
- **Phase 7** (US2 Application): Depends on Phase 4 + Phase 5 (domain + StreakCalculatorService for streak recalc)
- **Phase 8** (US3 Application): Depends on Phase 4 + Phase 5 (domain + StreakCalculatorService)
- **Phase 9** (Infrastructure): Depends on Phases 4 + 3 (domain ports + DB columns + generated jOOQ classes with new columns)
- **Phase 10** (Web): Depends on Phases 6-9 (all use cases + repos must exist)
- **Phase 11** (Integration Tests): Depends on Phase 10 (web layer complete)
- **Phase 12** (Polish): Depends on all previous phases

### User Story Dependencies

- **US4** (jOOQ Codegen, P1): Phases 1-2 — prerequisite for infrastructure, complete first
- **US1** (Habits CRUD, P1): Phase 4 domain + Phase 5 shared + Phase 6 use cases, then Phase 9-10 repos + web
- **US2** (Completion Logs, P1): Phase 4 domain + Phase 5 shared + Phase 7 use cases, then Phase 9-10 repos + web
- **US3** (Streaks, P2): Phase 4 domain + Phase 5 shared + Phase 8 use case, then Phase 9-10 repos + web

### Parallel Opportunities Within Phases

- **Phase 1**: T005-T006 (test URL updates) parallel
- **Phase 3**: T014-T015 (migrations) parallel
- **Phase 4**: T018-T021 (value objects) all parallel; T025-T027 (exceptions) all parallel
- **Phase 6**: T038-T042 (unit tests) all parallel after use cases written
- **Phase 7**: T046-T048 (unit tests) all parallel
- **Phase 9**: T051-T053 (repositories) all parallel

---

## Parallel Example: Phase 4 (Domain Layer) + Phases 1-3

```bash
# Domain can start in parallel with prerequisites:
# Stream A: Prerequisites
Task: T001-T007 "Phase 1: /api/v1 migration"
Task: T008-T013 "Phase 2: jOOQ codegen"
Task: T014-T017 "Phase 3: Liquibase migrations"

# Stream B: Domain (pure Java, no DB dependency)
Task: T018-T021 "Value objects & enums" (parallel)
Task: T022-T024 "Entities" (sequential)
Task: T025-T027 "Exceptions" (parallel)
Task: T028-T030 "Ports" (sequential)
```

---

## Implementation Strategy

### MVP First (US4 + US1)

1. Complete Phases 1-3: Prerequisites (API v1, jOOQ codegen, migrations)
   — Phase 4 can run in parallel with Phases 1-3
2. Complete Phase 5: StreakCalculatorService (shared dependency)
3. Complete Phase 6: US1 use cases + tests
4. Complete Phase 9 (T051 only): JooqHabitRepository
5. Complete Phase 10: Web layer for CRUD endpoints
6. **STOP and VALIDATE**: Habits CRUD works end-to-end

### Incremental Delivery

1. US4 (Phases 1-2) + Phase 4 (Domain, parallel) → jOOQ codegen + domain ready
2. Phase 5 → StreakCalculatorService ready (unblocks all stories)
3. US1 (Phase 6 + partial 9-10) → Habit CRUD end-to-end (MVP!)
4. US2 (Phase 7 + partial 9-10) → Completion logging works
5. US3 (Phase 8 + partial 9-10) → Streaks visible and accurate
6. Phases 11-12 → Integration tests + polish

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in same phase
- All domain classes: pure Java, no Spring/jOOQ/Jackson imports
- All repositories: generated jOOQ classes only, filter `deleted_at IS NULL`, include `userId` predicate in ALL queries (Constitution §III)
- All use cases with mutations across tables: @Transactional (CompleteHabitUseCase, DeleteHabitLogUseCase, UpdateHabitUseCase)
- StreakCalculatorService: NO @Transactional (application service with injected Clock, called within transactional use cases)
- HabitStreak is a pure value object — no id field in domain. Repository maps to/from DB row with id internally.
- Commit after each phase checkpoint per constitution commit granularity rules
