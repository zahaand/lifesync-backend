# Quickstart: Goals Feature

**Branch**: `006-goals-feature` | **Date**: 2026-03-31

## Prerequisites

- Docker Compose running (PostgreSQL 16, Kafka)
- `.env` file with required variables
- Maven build passing on `main` branch

## Build & Run

```bash
# Start infrastructure
docker compose up -d

# Build all modules
mvn clean install -DskipTests

# Run with dev profile
cd lifesync-app && mvn spring-boot:run
```

## Key Files to Modify (by layer)

### 1. API Spec (lifesync-api-spec)
- `src/main/resources/openapi/lifesync-api.yaml` — add goals paths and schemas from `contracts/goals-api.yaml`
- Run `mvn generate-sources` in lifesync-api-spec to regenerate GoalApi interface

### 2. Domain (lifesync-domain)
New package: `ru.zahaand.lifesync.domain.goal`
- `Goal.java` — immutable entity with copy-on-write mutation
- `GoalId.java` — record value object
- `GoalMilestone.java` — immutable entity
- `GoalMilestoneId.java` — record value object
- `GoalHabitLink.java` — entity
- `GoalHabitLinkId.java` — record value object
- `GoalStatus.java` — enum (ACTIVE, COMPLETED)
- `GoalRepository.java` — port interface
- `GoalMilestoneRepository.java` — port interface
- `GoalHabitLinkRepository.java` — port interface
- `exception/GoalNotFoundException.java`
- `exception/GoalHabitLinkNotFoundException.java`
- `exception/DuplicateGoalHabitLinkException.java`

### 3. Application (lifesync-application)
New package: `ru.zahaand.lifesync.application.goal`
- `CreateGoalUseCase.java`
- `GetGoalUseCase.java`
- `GetGoalsUseCase.java`
- `UpdateGoalUseCase.java`
- `DeleteGoalUseCase.java`
- `AddMilestoneUseCase.java`
- `UpdateMilestoneUseCase.java`
- `DeleteMilestoneUseCase.java`
- `LinkHabitToGoalUseCase.java`
- `UnlinkHabitFromGoalUseCase.java`
- `UpdateGoalProgressUseCase.java` — manual progress
- `RecalculateGoalProgressUseCase.java` — automatic, called by consumer

### 4. Infrastructure (lifesync-infrastructure)
New package: `ru.zahaand.lifesync.infrastructure.goal`
- `JooqGoalRepository.java` — implements GoalRepository
- `JooqGoalMilestoneRepository.java` — implements GoalMilestoneRepository
- `JooqGoalHabitLinkRepository.java` — implements GoalHabitLinkRepository

New consumers: `ru.zahaand.lifesync.infrastructure.goal`
- `GoalProgressConsumer.java` — listens `habit.log.completed`, calls RecalculateGoalProgressUseCase
- `GoalAnalyticsConsumer.java` — stub, listens `goal.progress.updated`
- `GoalNotificationConsumer.java` — stub, listens `goal.progress.updated`

### 5. Web (lifesync-web)
New package: `ru.zahaand.lifesync.web.goal`
- `GoalController.java` — implements generated GoalApi interface

### 6. App Config (lifesync-app)
- `config/UseCaseConfig.java` — add @Bean methods for all 12 goal use cases

### 7. Exception Handling (lifesync-web)
- `GlobalExceptionHandler.java` — add handlers for GoalNotFoundException (404), GoalHabitLinkNotFoundException (404), DuplicateGoalHabitLinkException (409)

## Testing Strategy

### Unit Tests (lifesync-domain, lifesync-application)
- Goal entity: update, progress update, soft-delete, status transitions
- GoalMilestone entity: complete, uncomplete, soft-delete
- Each use case: success path + validation + ownership checks
- Pattern: `@ExtendWith(MockitoExtension.class)`, `@Nested` per method

### Integration Tests (lifesync-app)
- `GoalControllerIT` — full CRUD via REST, pagination, status filter
- `GoalMilestoneControllerIT` — add/update/delete milestones
- `GoalHabitLinkControllerIT` — link/unlink, duplicate rejection
- `GoalProgressIT` — manual progress update, status transition
- `GoalProgressConsumerIT` — end-to-end: complete habit → goal progress updated → event published
- `GoalAnalyticsConsumerIT` — event processing, idempotency, DLQ
- Pattern: extends `BaseIT` with Testcontainers (PG + Kafka)

## Verification

```bash
# Run all tests
mvn verify

# Check coverage
# JaCoCo ≥ 80% on domain + application modules
```
