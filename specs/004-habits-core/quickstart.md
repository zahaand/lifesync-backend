# Quickstart: Habits Core

**Feature**: 004-habits-core | **Date**: 2026-03-30

## Prerequisites

- Java 21 LTS
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL + Kafka)
- RSA key pair in `lifesync-app/src/main/resources/certs/` (see `application-dev.yml.example`)

## Setup

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL 16 (port 5432) and Kafka (port 9092). Liquibase runs automatically on app startup.

### 2. Generate jOOQ classes (one-time, after DB schema is ready)

```bash
mvn generate-sources -P jooq-codegen -pl lifesync-infrastructure
```

This connects to the running PostgreSQL instance, introspects all tables, and generates type-safe classes to `target/generated-sources/jooq/`.

**Note**: PostgreSQL must be running with all migrations applied. Run the app once (or `mvn liquibase:update`) to apply migrations first.

### 3. Build

```bash
mvn clean install
```

### 4. Run

```bash
mvn spring-boot:run -pl lifesync-app -Dspring-boot.run.profiles=dev
```

### 5. Access API

- Swagger UI: http://localhost:8080/swagger-ui.html
- All habit endpoints require authentication. Obtain a JWT token via `POST /api/v1/auth/login`.

## Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v1/habits | Create a habit |
| GET | /api/v1/habits | List habits (paginated, filterable) |
| GET | /api/v1/habits/{id} | Get habit details |
| PATCH | /api/v1/habits/{id} | Update habit |
| DELETE | /api/v1/habits/{id} | Soft-delete habit |
| POST | /api/v1/habits/{id}/complete | Log completion |
| DELETE | /api/v1/habits/{id}/complete/{logId} | Remove completion log |
| GET | /api/v1/habits/{id}/logs | List completion logs |
| GET | /api/v1/habits/{id}/streak | Get streak data |

## Running Tests

```bash
# All tests (Testcontainers will start PostgreSQL automatically)
mvn test

# Only habit-related tests
mvn test -pl lifesync-application -Dtest="*Habit*"
mvn test -pl lifesync-web -Dtest="HabitControllerIT"
```

## Implementation Order

1. **Phase 0**: Migrate endpoints to `/api/v1`, configure jOOQ codegen, migrate existing repos
2. **Phase 1**: Liquibase migrations (V15-V16), domain entities & ports, OpenAPI YAML
3. **Phase 2**: StreakCalculatorService, all 9 use cases
4. **Phase 3**: jOOQ repositories, HabitController, exception handlers, config
5. **Phase 4**: Unit tests, integration tests, JaCoCo verification
