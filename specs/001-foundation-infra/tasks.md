# Tasks: Foundation Infrastructure

**Input**: Design documents from `/specs/001-foundation-infra/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: Not explicitly requested in the feature specification. No test tasks included.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Multi-module Maven**: Each module at repository root (`lifesync-domain/`, `lifesync-app/`, etc.)
- **Migrations**: `lifesync-infrastructure/src/main/resources/db/changelog/{domain}/`
- **App config**: `lifesync-app/src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Remove single-module scaffolding. No module code yet.

- [x] T001 Remove existing single-module source directory `src/` and replace root `pom.xml` with parent aggregator POM (packaging: pom, no `<parent>` to spring-boot-starter-parent, import Spring Boot BOM via `<dependencyManagement>` with `scope: import` and `type: pom`, declare all version properties, declare commons-lang3 in dependencyManagement, list 6 `<modules>` in canonical order) in `pom.xml`
- [x] T002 Add `.env` to `.gitignore`

**Checkpoint**: Root pom.xml is a valid aggregator. No child modules exist yet — build will fail (expected).

---

## Phase 2: User Story 1 — Multi-Module Project Build (Priority: P1)

**Goal**: All 6 Maven modules compile in dependency order with `mvn clean verify` exit code 0.

**Independent Test**: Run `./mvnw clean verify` from project root. All modules compile. Exit code 0.

### Implementation for User Story 1

- [x] T003 [P] [US1] Create `lifesync-api-spec/pom.xml` with packaging `pom`, parent pointing to root aggregator, no dependencies
- [x] T004 [P] [US1] Create `lifesync-domain/pom.xml` with packaging `jar`, parent pointing to root aggregator, zero dependencies; create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/.gitkeep`
- [x] T005 [P] [US1] Create `lifesync-application/pom.xml` with packaging `jar`, parent pointing to root aggregator, dependency on `lifesync-domain`; create `lifesync-application/src/main/java/ru/zahaand/lifesync/application/.gitkeep`
- [x] T006 [P] [US1] Create `lifesync-infrastructure/pom.xml` with packaging `jar`, parent pointing to root aggregator, dependencies on `lifesync-domain`, `lifesync-application`, `spring-boot-starter-jooq`, `liquibase-core`, `spring-kafka`, `postgresql` (runtime scope); create `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/.gitkeep`
- [x] T007 [P] [US1] Create `lifesync-web/pom.xml` with packaging `jar`, parent pointing to root aggregator, dependencies on `lifesync-application`, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-security`; create `lifesync-web/src/main/java/ru/zahaand/lifesync/web/.gitkeep`
- [x] T008 [US1] Create `lifesync-app/pom.xml` with packaging `jar`, parent pointing to root aggregator, dependencies on `lifesync-infrastructure`, `lifesync-web`, `spring-boot-starter-actuator`, test-scope dependencies (`spring-boot-starter-test`, `spring-kafka-test`, `spring-security-test`), configure `spring-boot-maven-plugin` with explicit version; move `LifesyncBackendApplication.java` to `lifesync-app/src/main/java/ru/zahaand/lifesync/app/LifesyncBackendApplication.java` and `LifesyncBackendApplicationTests.java` to `lifesync-app/src/test/java/ru/zahaand/lifesync/app/LifesyncBackendApplicationTests.java`
- [x] T009 [US1] Verify `./mvnw clean verify` succeeds from project root with all 6 modules compiling in order; verify lifesync-domain pom.xml has zero framework dependencies

**Checkpoint**: US1 complete. `mvn clean verify` passes. Module dependency boundaries enforced via POMs.

---

## Phase 3: User Story 2 — Local Development Environment (Priority: P2)

**Goal**: `docker compose up -d` starts PostgreSQL 16 and Kafka+Zookeeper. All credentials from `.env`.

**Independent Test**: Run `docker compose up -d`, verify PostgreSQL accepts connections on port 5432 and Kafka broker is reachable on port 9092.

### Implementation for User Story 2

- [x] T010 [P] [US2] Create `.env.example` at project root with placeholder variables: `POSTGRES_DB=lifesync`, `POSTGRES_USER=lifesync`, `POSTGRES_PASSWORD=lifesync`, `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=lifesync`, `DB_USERNAME=lifesync`, `DB_PASSWORD=lifesync`, `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- [x] T011 [US2] Create `docker-compose.yml` at project root with 3 services: `postgres` (image `postgres:16`, port 5432, env from `.env` for POSTGRES_DB/USER/PASSWORD, named volume `pgdata`), `zookeeper` (image `confluentinc/cp-zookeeper`, port 2181, ZOOKEEPER_CLIENT_PORT=2181), `kafka` (image `confluentinc/cp-kafka`, port 9092, depends_on zookeeper, KAFKA_BROKER_ID=1, KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181, KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092, KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1)
- [x] T012 [US2] Verify `docker compose up -d` starts all 3 services; verify PostgreSQL connection with `docker compose exec postgres psql -U lifesync -d lifesync -c "SELECT 1"`; verify Kafka broker is reachable; verify `.env` is not tracked by git

**Checkpoint**: US2 complete. Local backing services start with one command.

---

## Phase 4: User Story 3 — Database Schema Baseline (Priority: P3)

**Goal**: Liquibase creates all 11 tables on application startup. Every migration has a rollback block.

**Independent Test**: Start the application against a clean database. Verify all 11 tables exist with UUID PKs, correct audit columns, and soft-delete columns where specified.

### Implementation for User Story 3

- [x] T013 [US3] Create Liquibase master changelog at `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml` with `<include>` directives for all 11 migration files in order (user/ V1-V3, habit/ V4-V6, goal/ V7-V9, system/ V10-V11)
- [x] T014 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/user/V1__create_users.xml` — CREATE TABLE users (id UUID PK DEFAULT gen_random_uuid(), email VARCHAR(255) NN UQ, password_hash VARCHAR(255) NN, username VARCHAR(100) NN UQ, enabled BOOLEAN NN DEFAULT true, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); include `<rollback>` with DROP TABLE. No XML comments inside `<changeSet>`.
- [x] T015 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/user/V2__create_user_profiles.xml` — CREATE TABLE user_profiles (id UUID PK DEFAULT gen_random_uuid(), user_id UUID FK→users(id) NN UQ, display_name VARCHAR(150) NULL, timezone VARCHAR(50) NN DEFAULT 'UTC', locale VARCHAR(10) NN DEFAULT 'en', created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now()); include `<rollback>`.
- [x] T016 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/user/V3__create_refresh_tokens.xml` — CREATE TABLE refresh_tokens (id UUID PK DEFAULT gen_random_uuid(), user_id UUID FK→users(id) NN, token_hash VARCHAR(255) NN UQ, expires_at TIMESTAMPTZ NN, revoked BOOLEAN NN DEFAULT false); no audit columns; include `<rollback>`.
- [x] T017 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/habit/V4__create_habits.xml` — CREATE TABLE habits (id UUID PK DEFAULT gen_random_uuid(), user_id UUID FK→users(id) NN, name VARCHAR(200) NN, description TEXT NULL, frequency VARCHAR(20) NN, active BOOLEAN NN DEFAULT true, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); include `<rollback>`.
- [x] T018 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/habit/V5__create_habit_logs.xml` — CREATE TABLE habit_logs (id UUID PK DEFAULT gen_random_uuid(), habit_id UUID FK→habits(id) NN, user_id UUID FK→users(id) NN, log_date DATE NN, note TEXT NULL, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); UNIQUE(habit_id, log_date); include `<rollback>`.
- [x] T019 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/habit/V6__create_habit_streaks.xml` — CREATE TABLE habit_streaks (id UUID PK DEFAULT gen_random_uuid(), habit_id UUID FK→habits(id) NN UQ, current_streak INTEGER NN DEFAULT 0, longest_streak INTEGER NN DEFAULT 0, last_log_date DATE NULL, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now()); include `<rollback>`.
- [x] T020 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/goal/V7__create_goals.xml` — CREATE TABLE goals (id UUID PK DEFAULT gen_random_uuid(), user_id UUID FK→users(id) NN, title VARCHAR(200) NN, description TEXT NULL, target_date DATE NULL, status VARCHAR(20) NN DEFAULT 'ACTIVE', progress INTEGER NN DEFAULT 0, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); include `<rollback>`.
- [x] T021 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/goal/V8__create_goal_milestones.xml` — CREATE TABLE goal_milestones (id UUID PK DEFAULT gen_random_uuid(), goal_id UUID FK→goals(id) NN, title VARCHAR(200) NN, sort_order INTEGER NN DEFAULT 0, completed BOOLEAN NN DEFAULT false, completed_at TIMESTAMPTZ NULL, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); include `<rollback>`.
- [x] T022 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/goal/V9__create_goal_habits.xml` — CREATE TABLE goal_habits (id UUID PK DEFAULT gen_random_uuid(), goal_id UUID FK→goals(id) NN, habit_id UUID FK→habits(id) NN, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now()); UNIQUE(goal_id, habit_id); include `<rollback>`.
- [x] T023 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/system/V10__create_notification_logs.xml` — CREATE TABLE notification_logs (id UUID PK DEFAULT gen_random_uuid(), user_id UUID FK→users(id) NN, channel VARCHAR(30) NN, event_type VARCHAR(100) NN, status VARCHAR(20) NN, message TEXT NULL, sent_at TIMESTAMPTZ NULL, created_at TIMESTAMPTZ NN DEFAULT now(), updated_at TIMESTAMPTZ NN DEFAULT now(), deleted_at TIMESTAMPTZ NULL); include `<rollback>`.
- [x] T024 [P] [US3] Write migration `lifesync-infrastructure/src/main/resources/db/changelog/system/V11__create_processed_events.xml` — CREATE TABLE processed_events (id UUID PK DEFAULT gen_random_uuid(), event_id VARCHAR(255) NN UQ, event_type VARCHAR(100) NN, processed_at TIMESTAMPTZ NN DEFAULT now()); no audit columns, no soft delete; include `<rollback>`.
- [x] T025 [US3] Verify Liquibase migrations: start application against clean database, confirm all 11 tables created; verify UUID PKs, audit columns on 9 tables, soft-delete on 6 tables; verify each migration rolls back cleanly

**Checkpoint**: US3 complete. All 11 tables created via Liquibase with rollback capability.

---

## Phase 5: User Story 4 — Application Health Verification (Priority: P4)

**Goal**: Application starts with all modules wired. `/actuator/health` returns HTTP 200.

**Independent Test**: Start application with Docker services running. `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

### Implementation for User Story 4

- [x] T026 [US4] Create `lifesync-app/src/main/resources/application.yml` with datasource config (url, username, password via `${ENV_VAR:default}` pattern), Liquibase change-log path (`classpath:db/changelog/db.changelog-master.xml`), Kafka bootstrap-servers, and actuator config (expose health endpoint only, show-details: always)
- [x] T027 [US4] Configure permissive Spring Security filter chain in `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/SecurityConfig.java` — permit all requests (no authentication this sprint); use constructor injection, final fields, explicit constructor, no Lombok, no @Autowired
- [x] T028 [US4] Verify application starts with `./mvnw -pl lifesync-app spring-boot:run`; verify `curl http://localhost:8080/actuator/health` returns HTTP 200 with `{"status":"UP"}`; verify health endpoint reflects degraded status when database is stopped

**Checkpoint**: US4 complete. Application boots cleanly, health endpoint operational.

---

## Phase 6: User Story 5 — Continuous Integration Pipeline (Priority: P5)

**Goal**: GitHub Actions runs `mvn clean verify` on every push/PR to main.

**Independent Test**: Push a commit to main. Verify pipeline triggers, builds all modules, and reports pass/fail.

### Implementation for User Story 5

- [x] T029 [US5] Create `.github/workflows/ci.yml` — trigger on push and pull_request to main; runner ubuntu-latest; steps: `actions/checkout@v4`, `actions/setup-java@v4` (distribution: temurin, java-version: 21, cache: maven), `mvn clean verify --batch-mode --no-transfer-progress`; no Docker services in CI
- [x] T030 [US5] Verify CI pipeline: push commit to feature branch, open PR against main, confirm workflow triggers and completes successfully

**Checkpoint**: US5 complete. CI pipeline validates every push and PR.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup affecting multiple user stories.

- [x] T031 [P] Remove `HELP.md` from project root (generated by Spring Initializr, no longer needed)
- [x] T032 Verify full end-to-end flow: `docker compose up -d` → `./mvnw clean verify` → `./mvnw -pl lifesync-app spring-boot:run` → `curl /actuator/health` returns 200 → `docker compose down`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **US1 (Phase 2)**: Depends on Setup (Phase 1) completion — BLOCKS all other user stories
- **US2 (Phase 3)**: Depends on US1 (needs buildable project)
- **US3 (Phase 4)**: Depends on US1 (infrastructure module must exist) + US2 (database must be running for verification)
- **US4 (Phase 5)**: Depends on US1 + US2 + US3 (needs all modules, services, and migrations)
- **US5 (Phase 6)**: Depends on US1 (needs buildable project; independent of US2-US4 for CI config, but verification needs passing build)
- **Polish (Phase 7)**: Depends on all user stories complete

### Within Each User Story

- POM/config files before source files
- Migrations: master changelog (T013) before individual migration files (T014-T024) for consistency, but individual files are independent of each other [P]
- Configuration (T026) before security config (T027) before verification (T028)
- Verification task is always last within its phase

### Parallel Opportunities

```text
# Phase 2 — all 5 module POMs can be created in parallel:
T003 [P] lifesync-api-spec/pom.xml
T004 [P] lifesync-domain/pom.xml
T005 [P] lifesync-application/pom.xml
T006 [P] lifesync-infrastructure/pom.xml
T007 [P] lifesync-web/pom.xml
# Then T008 (lifesync-app depends on all), then T009 (verify)

# Phase 3 — env and compose in parallel:
T010 [P] .env.example
T011     docker-compose.yml (can reference .env.example for documentation)

# Phase 4 — all 11 migration files in parallel (after T013):
T014–T024 [P] all individual migration XML files

# Phase 7 — cleanup in parallel:
T031 [P] Remove HELP.md
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: US1 — Multi-Module Build (T003-T009)
3. **STOP and VALIDATE**: `./mvnw clean verify` passes
4. Commit and push

### Incremental Delivery

1. Setup + US1 → Buildable multi-module project (MVP)
2. Add US2 → Docker Compose local environment
3. Add US3 → 11 database tables via Liquibase
4. Add US4 → Application boots, health endpoint works
5. Add US5 → CI pipeline validates every change
6. Polish → Cleanup, end-to-end verification

### Suggested Commits (Conventional Commits)

1. `feat: restructure into 6-module Maven project` (T001-T009)
2. `feat: add Docker Compose for PostgreSQL and Kafka` (T010-T012)
3. `feat: add Liquibase migrations for all 11 tables` (T013-T025)
4. `feat: configure application with health endpoint` (T026-T028)
5. `ci: add GitHub Actions pipeline` (T029-T030)
6. `chore: remove Spring Initializr scaffolding` (T031-T032)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently verifiable at its checkpoint
- Migrations use Liquibase XML format with explicit `<rollback>` blocks
- No XML comments inside `<changeSet>` blocks (constitution V)
- No Lombok, no Hibernate, no Spring Data JPA anywhere
- Domain module must have zero framework dependencies
- Commit after each user story checkpoint
