# Feature Specification: Foundation Infrastructure

**Feature Branch**: `001-foundation-infra`
**Created**: 2026-03-27
**Status**: Draft
**Input**: User description: "Sprint 1 — Foundation Infrastructure for LifeSync Backend"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multi-Module Project Build (Priority: P1)

A developer clones the repository and runs a single build command from the project root. The build compiles all six modules in the correct dependency order and completes successfully with no errors or test failures.

**Why this priority**: Without a passing multi-module build, no other infrastructure work can be verified or integrated. This is the prerequisite for every subsequent story.

**Independent Test**: Can be fully tested by running the build command from a fresh clone and verifying a successful exit code.

**Acceptance Scenarios**:

1. **Given** a fresh clone of the repository, **When** a developer runs the build command from the project root, **Then** all six modules compile in dependency order and the build succeeds with exit code 0.
2. **Given** the multi-module project structure, **When** the domain module is compiled, **Then** it has zero framework dependencies (no Spring, jOOQ, Kafka, or Jackson).
3. **Given** the multi-module project structure, **When** the application module is compiled, **Then** it depends only on the domain module (no jOOQ, Kafka, or Spring MVC).
4. **Given** the multi-module project structure, **When** the infrastructure module is compiled, **Then** it depends on domain and application only.
5. **Given** the multi-module project structure, **When** the web module is compiled, **Then** it depends on application only.

---

### User Story 2 - Local Development Environment (Priority: P2)

A developer starts the required backing services (database and message broker) with a single command. All services become available and healthy without manual configuration beyond providing a credentials file.

**Why this priority**: A working local environment is required before database migrations or application startup can be tested. It unblocks stories 3 and 4.

**Independent Test**: Can be fully tested by running the environment startup command and verifying that both services accept connections.

**Acceptance Scenarios**:

1. **Given** a machine with a container runtime installed and a credentials file in place, **When** a developer runs the environment startup command, **Then** a PostgreSQL 16 instance and a Kafka broker with Zookeeper become available.
2. **Given** running backing services, **When** a developer connects to the database using credentials from the credentials file, **Then** the connection succeeds.
3. **Given** running backing services, **When** a developer connects to the Kafka broker, **Then** the connection succeeds.
4. **Given** the credentials file, **When** the repository is inspected, **Then** the credentials file is excluded from version control.

---

### User Story 3 - Database Schema Baseline (Priority: P3)

With the local database running, migrations execute automatically on application startup and create all 11 required tables. Every migration is reversible.

**Why this priority**: The schema baseline is the prerequisite for any future domain work. It depends on the local environment (US2) being available.

**Independent Test**: Can be fully tested by starting the application against a clean database and verifying all 11 tables exist with correct structure.

**Acceptance Scenarios**:

1. **Given** a running PostgreSQL instance with an empty database, **When** migrations run, **Then** all 11 tables are created: users, user_profiles, habits, habit_logs, habit_streaks, goals, goal_milestones, goal_habits, notification_logs, processed_events, refresh_tokens.
2. **Given** all migrations have been applied, **When** each migration is rolled back in reverse order, **Then** each rollback completes without errors and the corresponding table is removed.
3. **Given** the migration files, **When** the file structure is inspected, **Then** each file resides under its respective domain directory.
4. **Given** any table definition, **When** the primary key column is inspected, **Then** it uses UUID type.
5. **Given** tables that support deletion (users, habits, goals, habit_logs, goal_milestones, notification_logs), **When** the table schema is inspected, **Then** a soft-delete column (deleted_at) is present.

---

### User Story 4 - Application Health Verification (Priority: P4)

A developer starts the application with all modules wired together. The health endpoint returns a success response confirming all backing services are reachable.

**Why this priority**: Validates that the full module wiring, database connection, and application context load correctly. Depends on US1-US3.

**Independent Test**: Can be fully tested by starting the application and sending an HTTP request to the health endpoint.

**Acceptance Scenarios**:

1. **Given** running backing services and applied migrations, **When** the application starts, **Then** the application context loads successfully without errors.
2. **Given** a running application, **When** an HTTP GET request is sent to /actuator/health, **Then** the response status is 200 and the body indicates the system is healthy.
3. **Given** a running application, **When** the database becomes unreachable, **Then** the health endpoint reflects the degraded status.

---

### User Story 5 - Continuous Integration Pipeline (Priority: P5)

Every push and pull request to the main branch triggers an automated pipeline that builds all modules, runs all tests, and reports a clear pass/fail result.

**Why this priority**: CI ensures ongoing build health but is only valuable once there is a buildable project (US1) to validate. It is the last story because it wraps around all others.

**Independent Test**: Can be fully tested by pushing a commit to main and verifying the pipeline runs and reports a result.

**Acceptance Scenarios**:

1. **Given** a commit pushed to the main branch, **When** the CI pipeline is triggered, **Then** it builds all modules and runs all tests.
2. **Given** a pull request opened against main, **When** the CI pipeline is triggered, **Then** it builds all modules and runs all tests.
3. **Given** a passing build, **When** the pipeline completes, **Then** it reports a success status.
4. **Given** a failing test or compilation error, **When** the pipeline completes, **Then** it reports a failure status and the build is marked as failed.

---

### Edge Cases

- What happens when the container runtime is not running and a developer tries to start the local environment? The startup command MUST fail with a clear error message indicating the container runtime is unavailable.
- What happens when the database port is already in use? The environment startup MUST fail with an identifiable port-conflict error rather than silently starting on a different port.
- What happens when migrations are run against an already-migrated database? Already-applied changesets MUST be skipped and only new ones applied.
- What happens when a migration file is malformed? The startup MUST fail and report the specific changeset that failed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST be structured as six modules: lifesync-api-spec, lifesync-domain, lifesync-application, lifesync-infrastructure, lifesync-web, lifesync-app.
- **FR-002**: Module dependencies MUST follow strict inward-pointing rules: domain has no framework dependencies; application depends on domain only; infrastructure depends on domain and application; web depends on application; app depends on all modules.
- **FR-003**: The lifesync-api-spec module MUST exist as an empty placeholder for future OpenAPI definitions.
- **FR-004**: The project MUST build successfully from the root with a single build command.
- **FR-005**: A docker-compose.yml at the project root MUST define PostgreSQL 16 and Apache Kafka (with Zookeeper) services.
- **FR-006**: All service connection credentials MUST be sourced from a .env file that is excluded from version control.
- **FR-007**: A .env.example file MUST be provided with placeholder values as a template for developers.
- **FR-008**: Migrations MUST run automatically on application startup.
- **FR-009**: All 11 tables (users, user_profiles, habits, habit_logs, habit_streaks, goals, goal_milestones, goal_habits, notification_logs, processed_events, refresh_tokens) MUST be created via versioned migration files.
- **FR-010**: Every migration file MUST include a rollback block.
- **FR-011**: Migration files MUST be organised under db/changelog/{domain}/ using four domain directories: user/ (users, user_profiles, refresh_tokens), habit/ (habits, habit_logs, habit_streaks), goal/ (goals, goal_milestones, goal_habits), system/ (notification_logs, processed_events).
- **FR-012**: All tables MUST use UUID primary keys.
- **FR-013**: Tables supporting deletion MUST use soft delete via a deleted_at column.
- **FR-017**: All user-facing tables (users, user_profiles, habits, habit_logs, habit_streaks, goals, goal_milestones, goal_habits, notification_logs) MUST include created_at and updated_at audit columns. The processed_events and refresh_tokens tables are excluded.
- **FR-014**: The /actuator/health endpoint MUST return HTTP 200 when all services are available.
- **FR-015**: A CI pipeline MUST run on every push and pull request to main.
- **FR-016**: The CI pipeline MUST build all modules and run all tests, failing the build if any step fails.

### Key Entities

- **users**: Registered platform user. Core identity record. All other user-owned entities reference this via a foreign key.
- **user_profiles**: Extended user information (display name, timezone, preferences). One-to-one with users.
- **habits**: A trackable habit defined by a user. Attributes include name, frequency, and active status.
- **habit_logs**: A single completion entry for a habit on a given date. Many-to-one with habits.
- **habit_streaks**: Computed consecutive-day streak record for a habit. Tracks current and longest streaks.
- **goals**: A user-defined goal with target date and progress tracking.
- **goal_milestones**: An intermediate checkpoint within a goal. Many-to-one with goals.
- **goal_habits**: Association linking a habit to a goal. Many-to-many bridge between goals and habits.
- **notification_logs**: Record of notifications sent to a user (channel, status, timestamp).
- **processed_events**: Idempotency table for message consumers. Stores event IDs to detect duplicates.
- **refresh_tokens**: Stores active refresh tokens for authentication lifecycle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new developer can start the full local environment and run a passing build within 10 minutes of cloning the repository.
- **SC-002**: The build command completes successfully with all six modules compiled and zero test failures.
- **SC-003**: All 11 database tables are created automatically on first application startup without manual intervention.
- **SC-004**: Every migration can be rolled back individually without errors.
- **SC-005**: The health endpoint returns a success response within 2 seconds of the application completing startup.
- **SC-006**: The CI pipeline provides a pass/fail result within 10 minutes of being triggered.
- **SC-007**: No framework imports appear in the domain module after build.

## Clarifications

### Session 2026-03-27

- Q: Should all tables include created_at and updated_at audit columns? → A: Only user-facing tables (9 of 11). Exclude processed_events and refresh_tokens.
- Q: How should the 11 tables be grouped into domain directories? → A: 4 domains — user (users, user_profiles, refresh_tokens), habit (habits, habit_logs, habit_streaks), goal (goals, goal_milestones, goal_habits), system (notification_logs, processed_events).

## Assumptions

- Developers have a container runtime (Docker or compatible) installed locally.
- The target operating system supports running containerized PostgreSQL and Kafka.
- The repository is hosted on GitHub (CI pipeline targets GitHub Actions).
- No business logic, authentication, or user-facing endpoints are included in this sprint — those belong to future sprints.
- The lifesync-api-spec module is intentionally empty; OpenAPI YAML will be added in Sprint 2.
- Kafka producers and consumers are not configured in this sprint; only the broker service is provisioned for future use.
- The .env file pattern is sufficient for local development; production secret management is out of scope.
