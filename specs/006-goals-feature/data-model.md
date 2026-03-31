# Data Model: Goals Feature

**Branch**: `006-goals-feature` | **Date**: 2026-03-31

## Existing Database Tables (no changes needed)

All tables created by migrations V7, V8, V9 — already applied.

### goals (V7)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | uuid | NOT NULL | gen_random_uuid() | PK |
| user_id | uuid | NOT NULL | — | FK → users(id) CASCADE |
| title | varchar(200) | NOT NULL | — | |
| description | text | YES | — | |
| target_date | date | YES | — | |
| status | varchar(20) | NOT NULL | 'ACTIVE' | ACTIVE, COMPLETED |
| progress | integer | NOT NULL | 0 | 0-100, updated by both manual and automatic paths |
| created_at | timestamptz | NOT NULL | now() | |
| updated_at | timestamptz | NOT NULL | now() | |
| deleted_at | timestamptz | YES | — | Soft-delete |

Indexes: FK on user_id, idx on (user_id, deleted_at).

### goal_milestones (V8)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | uuid | NOT NULL | gen_random_uuid() | PK |
| goal_id | uuid | NOT NULL | — | FK → goals(id) CASCADE |
| title | varchar(200) | NOT NULL | — | |
| sort_order | integer | NOT NULL | 0 | |
| completed | boolean | NOT NULL | false | |
| completed_at | timestamptz | YES | — | Set when completed=true |
| created_at | timestamptz | NOT NULL | now() | |
| updated_at | timestamptz | NOT NULL | now() | |
| deleted_at | timestamptz | YES | — | Soft-delete |

Indexes: FK on goal_id.

### goal_habits (V9)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | uuid | NOT NULL | gen_random_uuid() | PK |
| goal_id | uuid | NOT NULL | — | FK → goals(id) CASCADE |
| habit_id | uuid | NOT NULL | — | FK → habits(id) CASCADE |
| created_at | timestamptz | NOT NULL | now() | |
| updated_at | timestamptz | NOT NULL | now() | |

Unique: uq_goal_habits_goal_habit (goal_id, habit_id). No soft-delete — hard link table.

## Domain Entities

### Goal

```
Goal (final class, immutable with copy-on-write)
├── GoalId id           — record GoalId(UUID value)
├── UUID userId
├── String title        — required, max 200 chars
├── String description  — nullable
├── LocalDate targetDate — nullable
├── GoalStatus status   — enum: ACTIVE, COMPLETED
├── int progress        — 0-100
├── Instant createdAt
├── Instant updatedAt
└── Instant deletedAt   — nullable (soft-delete)

Methods:
├── update(title, description, targetDate, status, Instant now) → Goal
├── updateProgress(int progress, Instant now) → Goal  // also sets COMPLETED if 100
├── softDelete(Instant now) → Goal
├── isDeleted() → boolean
└── isActive() → boolean  // status == ACTIVE && !isDeleted()
```

### GoalMilestone

```
GoalMilestone (final class, immutable with copy-on-write)
├── GoalMilestoneId id  — record GoalMilestoneId(UUID value)
├── GoalId goalId
├── String title        — required, max 200 chars
├── int sortOrder
├── boolean completed
├── Instant completedAt — nullable
├── Instant createdAt
├── Instant updatedAt
└── Instant deletedAt   — nullable (soft-delete)

Methods:
├── complete(Instant now) → GoalMilestone
├── uncomplete(Instant now) → GoalMilestone
├── update(title, sortOrder, Instant now) → GoalMilestone
├── softDelete(Instant now) → GoalMilestone
└── isDeleted() → boolean
```

### GoalHabitLink

```
GoalHabitLink (final class)
├── GoalHabitLinkId id  — record GoalHabitLinkId(UUID value)
├── GoalId goalId
├── HabitId habitId     — reuses existing HabitId from habit domain
├── Instant createdAt
└── Instant updatedAt
```

### GoalStatus (enum)

```
ACTIVE, COMPLETED
```

## Domain Port Interfaces

### GoalRepository

```
save(Goal) → Goal
findByIdAndUserId(GoalId, UUID userId) → Optional<Goal>
findAllActiveByUserId(UUID userId, int page, int size) → GoalPage
update(Goal) → Goal

record GoalPage(List<Goal> content, long totalElements, int totalPages, int page, int size)
```

### GoalMilestoneRepository

```
save(GoalMilestone) → GoalMilestone
findByIdAndGoalId(GoalMilestoneId, GoalId) → Optional<GoalMilestone>
findAllActiveByGoalId(GoalId) → List<GoalMilestone>
update(GoalMilestone) → GoalMilestone
```

### GoalHabitLinkRepository

```
save(GoalHabitLink) → GoalHabitLink
existsByGoalIdAndHabitId(GoalId, HabitId) → boolean
findAllByGoalId(GoalId) → List<GoalHabitLink>
findActiveGoalIdsByHabitId(HabitId) → List<GoalId>  // goals where deleted_at IS NULL
deleteByGoalIdAndHabitId(GoalId, HabitId) → void
countTotalByGoalId(GoalId) → int
countCompletedByGoalIdAndDate(GoalId, LocalDate) → int  // joins with habit_logs
```

## Domain Exceptions

```
GoalNotFoundException extends RuntimeException
GoalHabitLinkNotFoundException extends RuntimeException
DuplicateGoalHabitLinkException extends RuntimeException
```

## Relationships

```
users 1──────N goals
goals 1──────N goal_milestones
goals N──────N habits (via goal_habits junction)
```

## Progress Flow

### Automatic (Kafka consumer path)
```
habit.log.completed topic
  → GoalProgressConsumer (infra)
    → RecalculateGoalProgressUseCase (app)
      → GoalHabitLinkRepository.findActiveGoalIdsByHabitId(habitId)
      → For each goalId:
          total = countTotalByGoalId(goalId)
          completed = countCompletedByGoalIdAndDate(goalId, logDate)
          progress = Math.round((float) completed / total * 100)
          goal.updateProgress(progress, now)
          goalRepository.update(goal)
          → ApplicationEventPublisher.publishEvent(GoalProgressUpdatedEvent)
            → KafkaGoalEventPublisher (AFTER_COMMIT)
              → goal.progress.updated topic
                → GoalAnalyticsConsumer (stub)
                → GoalNotificationConsumer (stub)
```

### Manual (REST endpoint path)
```
PATCH /api/v1/goals/{id}/progress
  → GoalController
    → UpdateGoalProgressUseCase (app)
      → goalRepository.findByIdAndUserId(goalId, userId)
      → goal.updateProgress(progress, now)
      → goalRepository.update(goal)
      → ApplicationEventPublisher.publishEvent(GoalProgressUpdatedEvent)
        → KafkaGoalEventPublisher (AFTER_COMMIT)
          → goal.progress.updated topic
            → GoalAnalyticsConsumer (stub)
            → GoalNotificationConsumer (stub)
```
