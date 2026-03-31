# Implementation Plan: Goals Feature

**Branch**: `006-goals-feature` | **Date**: 2026-03-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-goals-feature/spec.md`

## Summary

Full goals feature: CRUD operations, milestone management, habit-goal linking with automatic progress calculation, manual progress updates, and Kafka-based event-driven consumers (analytics + notification stubs). Reuses existing DB schema (V7-V9), existing `GoalProgressUpdatedEvent`, and `KafkaGoalEventPublisher`. Follows established hexagonal architecture patterns from the habits feature.

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0  
**Storage**: PostgreSQL 16 (existing tables: goals, goal_milestones, goal_habits)  
**Testing**: JUnit 5 + AssertJ + Mockito + Testcontainers (PostgreSQL + Kafka)  
**Target Platform**: Linux server (Docker Compose for local dev)  
**Project Type**: Web service (REST API with Kafka event-driven consumers)  
**Performance Goals**: < 2s per CRUD operation, < 5s for async progress recalculation  
**Constraints**: User data isolation (userId predicate on all queries), JaCoCo ≥ 80%  
**Scale/Scope**: Single-user B2C habit/goal tracking platform

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Design Gate (all pass)

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | domain → application → infrastructure → web layering. No Spring in domain, no jOOQ in application. |
| II | API First | PASS | Goals endpoints added directly to the existing `lifesync-api.yaml` before implementation. One YAML = one source of truth. Controller will implement generated interface. |
| III | User Data Isolation | PASS | All repository queries include userId predicate. UseCase validates ownership before mutation. |
| IV | Single Responsibility | PASS | 12 use cases, one per operation. Consumers delegate to use cases. No business logic in repositories. |
| V | Liquibase Migrations | PASS | No new migrations needed — V7, V8, V9 already applied and cover all required schema. |
| VI | Secrets via Env Vars | PASS | No new secrets introduced. |
| VII | Portfolio Readability | PASS | No Lombok. No speculative features. All identifiers in English. |
| VIII | Logging Standards | PASS | Logger via LoggerFactory. DEBUG for params, INFO for success, WARN for duplicates, ERROR for failures. MDC for traceId/userId. |
| IX | Code Style | PASS | Final fields, constructor injection, explicit constructors, member order convention. |
| X | Testing Standards | PASS | Unit: MockitoExtension. IT: Testcontainers. @Nested per method. @DisplayName in English. JaCoCo ≥ 80%. |
| XI | Code & Doc Language | PASS | Code in English. Commits: Russian body, Conventional Commits type. |
| XII | OpenAPI Documentation | PASS | Every endpoint has summary, multi-line description with business rules and how-to-test, named examples, error explanations, field descriptions. |

### Post-Design Gate (all pass)

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | `GoalHabitLinkRepository.countCompletedDaysByGoalId()` / `countExpectedCompletionsByGoalId()` — ports in domain, joins in infra. Domain has no infra imports. |
| II | API First | PASS | All 12 endpoints and 11 schemas designed for addition to the single `lifesync-api.yaml`. One YAML = one source of truth (Principle II). |
| III | User Data Isolation | PASS | `findByIdAndUserId`, `findAllByUserId` patterns. Ownership check before every mutation. Consumer uses userId from event. |
| V | Liquibase Migrations | PASS | Verified existing migrations: V7 goals, V8 goal_milestones, V9 goal_habits — all columns match domain model exactly. |
| XII | OpenAPI Documentation | PASS | All 12 endpoints documented with summary, description (business rules + how-to-test), examples, error codes with explanations, field descriptions on all schemas. |

## Project Structure

### Documentation (this feature)

```text
specs/006-goals-feature/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 research decisions
├── data-model.md        # Entity model and flow diagrams
├── quickstart.md        # Dev setup and file map
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
lifesync-api-spec/src/main/resources/openapi/
└── lifesync-api.yaml                    # ADD goals paths + schemas

lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/
├── goal/
│   ├── Goal.java                        # NEW entity
│   ├── GoalId.java                      # NEW value object
│   ├── GoalMilestone.java               # NEW entity
│   ├── GoalMilestoneId.java             # NEW value object
│   ├── GoalHabitLink.java               # NEW entity
│   ├── GoalHabitLinkId.java             # NEW value object
│   ├── GoalStatus.java                  # NEW enum
│   ├── GoalRepository.java              # NEW port
│   ├── GoalMilestoneRepository.java     # NEW port
│   ├── GoalHabitLinkRepository.java     # NEW port
│   └── exception/
│       ├── GoalNotFoundException.java           # NEW
│       ├── GoalHabitLinkNotFoundException.java  # NEW
│       └── DuplicateGoalHabitLinkException.java # NEW
└── event/
    └── DomainEvent.java                 # NO CHANGE (already permits GoalProgressUpdatedEvent)

lifesync-application/src/main/java/ru/zahaand/lifesync/application/
└── goal/
    ├── CreateGoalUseCase.java                  # NEW
    ├── GetGoalUseCase.java                     # NEW
    ├── GetGoalsUseCase.java                    # NEW
    ├── UpdateGoalUseCase.java                  # NEW
    ├── DeleteGoalUseCase.java                  # NEW
    ├── AddMilestoneUseCase.java                # NEW
    ├── UpdateMilestoneUseCase.java             # NEW
    ├── DeleteMilestoneUseCase.java             # NEW
    ├── LinkHabitToGoalUseCase.java             # NEW
    ├── UnlinkHabitFromGoalUseCase.java         # NEW
    ├── UpdateGoalProgressUseCase.java          # NEW (manual)
    └── RecalculateGoalProgressUseCase.java     # NEW (automatic)

lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/
└── goal/
    ├── JooqGoalRepository.java                 # NEW
    ├── JooqGoalMilestoneRepository.java        # NEW
    ├── JooqGoalHabitLinkRepository.java        # NEW
    ├── GoalProgressConsumer.java               # NEW — listens habit.log.completed
    ├── GoalAnalyticsConsumer.java              # NEW stub — listens goal.progress.updated
    └── GoalNotificationConsumer.java           # NEW stub — listens goal.progress.updated

lifesync-web/src/main/java/ru/zahaand/lifesync/web/
├── goal/
│   └── GoalController.java                     # NEW — implements generated GoalApi
└── user/
    └── GlobalExceptionHandler.java              # MODIFY — add goal exception handlers

lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/
└── UseCaseConfig.java                           # MODIFY — add 12 goal use case beans
```

**Structure Decision**: Follows existing Maven multi-module hexagonal layout. New `goal` packages in each module mirror the existing `habit` packages. No new modules needed.

## Implementation Phases

### Phase 1: OpenAPI Spec Update
- Add Goals endpoints directly to the existing `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml` file. Do NOT create a separate YAML file. One YAML = one source of truth (Principle II).
- Add all goals paths (`/api/v1/goals/**`) and schemas (GoalCreateRequestDto, GoalResponseDto, GoalDetailResponseDto, GoalPageResponseDto, GoalUpdateRequestDto, GoalProgressUpdateRequestDto, MilestoneCreateRequestDto, MilestoneUpdateRequestDto, MilestoneResponseDto, GoalHabitLinkRequestDto, GoalHabitLinkResponseDto) to the existing spec
- Run `mvn generate-sources` to generate GoalApi interface and DTOs
- **Constitution**: Principle II (API First) — YAML before implementation

### Phase 2: Domain Layer
- Goal, GoalId, GoalStatus, GoalMilestone, GoalMilestoneId, GoalHabitLink, GoalHabitLinkId
- Repository port interfaces (GoalRepository, GoalMilestoneRepository, GoalHabitLinkRepository)
- Domain exceptions (GoalNotFoundException, GoalHabitLinkNotFoundException, DuplicateGoalHabitLinkException)
- Unit tests for entity mutation methods and validation
- **Constitution**: Principle I (no Spring/jOOQ imports), VII (no Lombok), IX (code style)

### Phase 3: Application Layer — Use Cases
- 12 use cases following established patterns (plain Java, @Transactional on writes, try/catch event publishing)
- CreateGoalUseCase, GetGoalUseCase, GetGoalsUseCase, UpdateGoalUseCase, DeleteGoalUseCase
- AddMilestoneUseCase, UpdateMilestoneUseCase, DeleteMilestoneUseCase
- LinkHabitToGoalUseCase (validates habit ownership via HabitRepository), UnlinkHabitFromGoalUseCase
- UpdateGoalProgressUseCase (manual), RecalculateGoalProgressUseCase (automatic)
- Unit tests for all use cases
- **Constitution**: Principle I (no jOOQ/Kafka imports), III (ownership validation), IV (SRP), X (testing)

### Phase 4: Infrastructure Layer — Repositories
- JooqGoalRepository, JooqGoalMilestoneRepository, JooqGoalHabitLinkRepository
- `countCompletedDaysByGoalId` joins goal_habits with habit_logs for distinct completion dates
- `countExpectedCompletionsByGoalId(GoalId, LocalDate createdAt, LocalDate endDate)` calculates expected completions per habit frequency (DAILY/WEEKLY/CUSTOM) in [createdAt, endDate], joins goal_habits → habits
- All reads include `DELETED_AT.isNull()` and `userId` predicates where applicable
- **Constitution**: Principle III (userId isolation), V (no DDL — queries only)

### Phase 5: Infrastructure Layer — Kafka Consumers
- GoalProgressConsumer: listens `habit.log.completed`, consumer group `lifesync-goal-progress`, delegates to RecalculateGoalProgressUseCase
- GoalAnalyticsConsumer: listens `goal.progress.updated`, consumer group `lifesync-goal-analytics`, stub (INFO log)
- GoalNotificationConsumer: listens `goal.progress.updated`, consumer group `lifesync-goal-notifier`, stub (INFO log)
- All three use ProcessedEventRepository for idempotency
- **Constitution**: Principle VIII (logging), Development Standard 10 (idempotency)

### Phase 6: Web Layer & Config
- GoalController implements generated GoalApi
- GlobalExceptionHandler: add GoalNotFoundException (404), GoalHabitLinkNotFoundException (404), DuplicateGoalHabitLinkException (409)
- UseCaseConfig: add @Bean methods for all 12 goal use cases
- **Constitution**: Principle II (generated interfaces), XII (OpenAPI docs)

### Phase 7: Integration Tests
- GoalControllerIT: full CRUD, pagination, status filter
- GoalMilestoneControllerIT: add/update/delete milestones
- GoalHabitLinkControllerIT: link/unlink, duplicate rejection
- GoalProgressIT: manual progress + status transition
- GoalProgressConsumerIT: end-to-end habit completion → goal progress → event
- GoalConsumerIT: analytics + notification stubs, idempotency, DLQ
- **Constitution**: Principle X (Testcontainers, @Nested, @DisplayName, JaCoCo ≥ 80%)

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Progress calculation | Frequency-aware formula | (distinct dates with at least one linked habit completed / total expected completions based on habit frequency) * 100, rounded via Math.round |
| Cross-domain query | GoalHabitLinkRepository port with infra-level join | Clean hexagonal: domain defines contract, infra handles SQL join |
| Manual progress events | Same ApplicationEventPublisher path | Consistent downstream processing for both manual and automatic |
| GetGoal enrichment | Return linked habit IDs (not full objects) | Avoids cross-domain entity loading; frontend fetches details separately |
| No new migrations | Reuse V7-V9 | Existing schema matches all requirements exactly |
| Consumer naming | GoalProgressConsumer, GoalAnalyticsConsumer, GoalNotificationConsumer | Follows {Purpose}Consumer convention from constitution |

## Complexity Tracking

No constitution violations. No complexity overrides needed.
