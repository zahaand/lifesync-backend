# Implementation Plan: Foundation Infrastructure

**Branch**: `001-foundation-infra` | **Date**: 2026-03-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-foundation-infra/spec.md`

## Summary

Restructure a single-module Spring Initializr project into a 6-module Maven
multi-module project with strict hexagonal dependency boundaries. Provision
PostgreSQL 16 and Kafka via Docker Compose, create all 11 database tables via
Liquibase migrations with rollback blocks, wire the Spring Boot application
context across modules, expose /actuator/health, and add a GitHub Actions CI
pipeline.

## Technical Context

**Language/Version**: Java 21 LTS
**Primary Dependencies**: Spring Boot 3.5.x, jOOQ 3.19, Liquibase 4.x,
Spring Kafka 3.x, Spring Security 6.x
**Storage**: PostgreSQL 16 (via Docker Compose locally)
**Testing**: JUnit 5 + AssertJ + Mockito (unit only this sprint;
no Testcontainers yet)
**Target Platform**: Linux/macOS server (JVM 21)
**Project Type**: Web service (multi-module Maven)
**Performance Goals**: N/A for infrastructure sprint
**Constraints**: No Hibernate, no Spring Data JPA, no Lombok. Domain module
must have zero framework imports.
**Scale/Scope**: 11 database tables, 6 Maven modules, single developer

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture (NON-NEG) | PASS | 6 modules enforce layer boundaries via POM dependencies |
| II | API First (NON-NEG) | N/A | No controllers or OpenAPI this sprint |
| III | User Data Isolation (NON-NEG) | N/A | No queries this sprint |
| IV | Single Responsibility | PASS | Each module has single purpose |
| V | Database Migrations via Liquibase (NON-NEG) | PASS | All tables via Liquibase with rollback blocks |
| VI | Secrets via Environment Variables | PASS | .env file, excluded from git |
| VII | Portfolio Readability (NON-NEG) | PASS | No Lombok, explicit Java, English identifiers |
| VIII | Logging Standards (NON-NEG) | N/A | No application code with logging this sprint |
| IX | Code Style (NON-NEG) | PASS | Constructor injection, final fields, explicit constructors |
| X | Testing Standards (NON-NEG) | N/A | No domain/application code to test this sprint |
| XI | Code and Documentation Language | PASS | English only |
| DS-1 | Maven modules canonical order | PASS | api-spec → domain → application → infrastructure → web → app |
| DS-2 | Package pattern | PASS | ru.zahaand.lifesync.{module}.{domain} |
| DS-6 | Local run: docker compose + .env | PASS | docker-compose.yml + .env.example provided |
| DS-12 | commons-lang3 in parent POM | PASS | Declared in dependencyManagement |

**GATE RESULT: PASS** — No violations. Proceeding to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation-infra/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
pom.xml                              # Parent POM (packaging: pom)
docker-compose.yml                   # PostgreSQL 16 + Kafka + Zookeeper
.env.example                         # Placeholder credentials
.github/workflows/ci.yml             # GitHub Actions CI

lifesync-api-spec/
└── pom.xml                          # Empty placeholder (packaging: pom)

lifesync-domain/
├── pom.xml                          # No dependencies
└── src/main/java/ru/zahaand/lifesync/domain/
    └── .gitkeep

lifesync-application/
├── pom.xml                          # Depends on: lifesync-domain
└── src/main/java/ru/zahaand/lifesync/application/
    └── .gitkeep

lifesync-infrastructure/
├── pom.xml                          # Depends on: domain + application
└── src/main/resources/
    └── db/changelog/
        ├── db.changelog-master.xml  # Root changelog
        ├── user/
        │   ├── V1__create_users.xml
        │   ├── V2__create_user_profiles.xml
        │   └── V3__create_refresh_tokens.xml
        ├── habit/
        │   ├── V4__create_habits.xml
        │   ├── V5__create_habit_logs.xml
        │   └── V6__create_habit_streaks.xml
        ├── goal/
        │   ├── V7__create_goals.xml
        │   ├── V8__create_goal_milestones.xml
        │   └── V9__create_goal_habits.xml
        └── system/
            ├── V10__create_notification_logs.xml
            └── V11__create_processed_events.xml

lifesync-web/
├── pom.xml                          # Depends on: lifesync-application
└── src/main/java/ru/zahaand/lifesync/web/
    └── .gitkeep

lifesync-app/
├── pom.xml                          # Depends on: all modules
└── src/
    ├── main/
    │   ├── java/ru/zahaand/lifesync/app/
    │   │   └── LifesyncBackendApplication.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/ru/zahaand/lifesync/app/
            └── LifesyncBackendApplicationTests.java
```

**Structure Decision**: Maven multi-module project. Each architectural layer is
its own module with POM-enforced dependency boundaries. The existing single-module
`src/` directory content moves into `lifesync-app`. Liquibase migrations live in
`lifesync-infrastructure` since they are infrastructure concerns. The Spring Boot
main class and application.yml live in `lifesync-app` which aggregates all modules.

## Parent POM Design

The parent POM is the root `pom.xml` with `packaging: pom`. Key design decisions:

1. **No `<parent>` to spring-boot-starter-parent**. Instead, import the Spring Boot
   BOM via `<dependencyManagement>` with `scope: import`, `type: pom`. This frees
   the parent slot for the project's own aggregator.

2. **Version properties** declared in `<properties>`:
   - `spring-boot.version` = 3.5.13
   - `jooq.version` = 3.19.x (managed by Spring Boot BOM)
   - `liquibase.version` (managed by Spring Boot BOM)
   - `spring-kafka.version` (managed by Spring Boot BOM)
   - `postgresql.version` (managed by Spring Boot BOM)
   - `commons-lang3.version` (managed by Spring Boot BOM)

3. **`<dependencyManagement>` only** — no `<dependencies>` block in parent.
   Each child module declares only what it needs.

4. **`commons-lang3`** listed in `<dependencyManagement>` as required by
   constitution DS-12.

5. **`<modules>` block** lists children in canonical order:
   lifesync-api-spec, lifesync-domain, lifesync-application,
   lifesync-infrastructure, lifesync-web, lifesync-app.

6. **Java compiler settings**: `maven.compiler.source` = 21,
   `maven.compiler.target` = 21.

## Per-Module Dependency Allocation

| Module | Dependencies | Scope |
|--------|-------------|-------|
| lifesync-api-spec | (none) | packaging: pom |
| lifesync-domain | (none) | Pure Java |
| lifesync-application | lifesync-domain | compile |
| lifesync-infrastructure | lifesync-domain, lifesync-application, spring-boot-starter-jooq, liquibase-core, spring-kafka, postgresql | postgresql: runtime |
| lifesync-web | lifesync-application, spring-boot-starter-web, spring-boot-starter-validation, spring-boot-starter-security | compile |
| lifesync-app | lifesync-infrastructure, lifesync-web, spring-boot-starter-actuator, spring-boot-starter-test, spring-kafka-test, spring-security-test | test deps: test scope |

## Docker Compose Design

File: `docker-compose.yml` at project root.

**Services**:
- `postgres`: image `postgres:16`, port 5432, env from `.env`
  (POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD), named volume for data
- `zookeeper`: image `confluentinc/cp-zookeeper`, port 2181
- `kafka`: image `confluentinc/cp-kafka`, port 9092, depends_on zookeeper,
  KAFKA_ADVERTISED_LISTENERS pointing to localhost:9092

**Files**:
- `.env.example`: all vars with placeholder values
- `.env`: added to `.gitignore`

## Liquibase Migration Design

**Changelog root**: `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml`

Master changelog includes domain changelogs in order:
1. `user/V1__create_users.xml` through `user/V3__create_refresh_tokens.xml`
2. `habit/V4__create_habits.xml` through `habit/V6__create_habit_streaks.xml`
3. `goal/V7__create_goals.xml` through `goal/V9__create_goal_habits.xml`
4. `system/V10__create_notification_logs.xml` through `system/V11__create_processed_events.xml`

**Format**: Liquibase XML format. Each file contains:
- `<changeSet id="..." author="...">` block with CREATE TABLE SQL
- Explicit `<rollback>` block with DROP TABLE statement

**No XML comments** inside `<changeSet>` blocks (constitution V).

## Application Configuration

File: `lifesync-app/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:lifesync}
    username: ${DB_USERNAME:lifesync}
    password: ${DB_PASSWORD:lifesync}
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

All sensitive values use `${ENV_VAR:default}` pattern.
Defaults are for local development only (match .env.example).

## CI Pipeline Design

File: `.github/workflows/ci.yml`

- **Triggers**: push to main, pull_request to main
- **Runner**: ubuntu-latest
- **Steps**:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` with distribution: temurin, java-version: 21,
     cache: maven
  3. `mvn clean verify --batch-mode --no-transfer-progress`
- **No Docker services** in CI this sprint (no integration tests yet)

## Complexity Tracking

> No constitution violations to justify. All gates pass.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |
