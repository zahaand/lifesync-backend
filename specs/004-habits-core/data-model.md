# Data Model: Habits Core

**Feature**: 004-habits-core | **Date**: 2026-03-30

## Entities

### Habit

Represents a trackable behavior belonging to a user.

| Field | Type | Source (DB Column) | Constraints | Notes |
|-------|------|--------------------|-------------|-------|
| id | UUID (HabitId) | habits.id | PK, gen_random_uuid() | |
| userId | UUID (UserId) | habits.user_id | FK → users.id, NOT NULL | Data isolation predicate |
| title | String | habits.name | NOT NULL, varchar(200) | DB uses "name", domain/API use "title" |
| description | String | habits.description | nullable, text | |
| frequency | Frequency | habits.frequency | NOT NULL, varchar(20) | Enum: DAILY, WEEKLY, CUSTOM |
| targetDaysOfWeek | DayOfWeekSet | habits.target_days_of_week | nullable, JSONB | Required when frequency=CUSTOM; JSONB array of day names |
| reminderTime | LocalTime | habits.reminder_time | nullable, TIME | New column (V16) |
| active | boolean | habits.active | NOT NULL, default true | |
| createdAt | Instant | habits.created_at | NOT NULL, timestamptz | |
| updatedAt | Instant | habits.updated_at | NOT NULL, timestamptz | |
| deletedAt | Instant | habits.deleted_at | nullable, timestamptz | Soft delete marker |

**Identity**: UUID primary key, auto-generated.
**Uniqueness**: No unique constraint on title (users may have similarly named habits).
**Lifecycle**: Created → Active/Inactive (toggle via isActive) → Soft-deleted (deletedAt set).

**Validation Rules**:
- title: required, max 200 characters
- frequency: required, must be DAILY, WEEKLY, or CUSTOM
- targetDaysOfWeek: required and non-empty when frequency=CUSTOM; ignored for DAILY/WEEKLY
- reminderTime: optional, HH:mm format

**Domain Methods**:
- `softDelete(Instant now)` — sets deletedAt
- `update(title, description, frequency, targetDaysOfWeek, reminderTime, active, Instant now)` — returns new instance with updated fields
- `isDeleted()` — deletedAt != null
- `isActive()` — active && !isDeleted()

---

### HabitLog

Represents a single completion event for a habit on a specific date.

| Field | Type | Source (DB Column) | Constraints | Notes |
|-------|------|--------------------|-------------|-------|
| id | UUID (HabitLogId) | habit_logs.id | PK, gen_random_uuid() | |
| habitId | UUID (HabitId) | habit_logs.habit_id | FK → habits.id CASCADE, NOT NULL | |
| userId | UUID (UserId) | habit_logs.user_id | FK → users.id CASCADE, NOT NULL | Data isolation predicate |
| logDate | LocalDate | habit_logs.log_date | NOT NULL, date | |
| note | String | habit_logs.note | nullable, text | |
| createdAt | Instant | habit_logs.created_at | NOT NULL, timestamptz | |
| updatedAt | Instant | habit_logs.updated_at | NOT NULL, timestamptz | |
| deletedAt | Instant | habit_logs.deleted_at | nullable, timestamptz | Soft delete marker |

**Identity**: UUID primary key, auto-generated.
**Uniqueness**: Composite unique constraint on (habit_id, log_date) — one completion per habit per date.
**Lifecycle**: Created → Soft-deleted (deletedAt set). No update flow.

**Validation Rules**:
- habitId: required, must reference an active, non-deleted habit owned by the user
- logDate: required, date format
- note: optional, text

**Domain Methods**:
- `softDelete(Instant now)` — sets deletedAt
- `isDeleted()` — deletedAt != null

---

### HabitStreak

Aggregated streak data for a habit. One-to-one relationship with Habit.

| Field | Type | Source (DB Column) | Constraints | Notes |
|-------|------|--------------------|-------------|-------|
| id | UUID | habit_streaks.id | PK, gen_random_uuid() | |
| habitId | UUID (HabitId) | habit_streaks.habit_id | FK → habits.id CASCADE, UNIQUE, NOT NULL | One streak per habit |
| currentStreak | int | habit_streaks.current_streak | NOT NULL, default 0 | |
| longestStreak | int | habit_streaks.longest_streak | NOT NULL, default 0 | |
| lastLogDate | LocalDate | habit_streaks.last_log_date | nullable, date | null when no completions |
| createdAt | Instant | habit_streaks.created_at | NOT NULL, timestamptz | |
| updatedAt | Instant | habit_streaks.updated_at | NOT NULL, timestamptz | |

**Identity**: UUID primary key. Unique constraint on habit_id.
**Lifecycle**: Created when habit is created (initialized 0/0/null) → Updated on every completion log/delete.

**Modeled as**: Value object (record) in domain layer. The repository handles persistence details.

---

## Value Objects

### Frequency (enum)

```
DAILY   — track every day
WEEKLY  — track at least once per week
CUSTOM  — track on specific days of the week
```

### DayOfWeekSet (record)

Wraps `Set<DayOfWeek>` (from `java.time.DayOfWeek`). Immutable, unmodifiable set.

**Validation**: Must be non-empty when used (CUSTOM frequency).
**JSONB Storage**: `["MONDAY","WEDNESDAY","FRIDAY"]` — array of day name strings.
**Mapping**: `DayOfWeek.valueOf(name)` for deserialization.

### HabitId, HabitLogId (records)

UUID wrapper records with null-check in constructor. Provides type safety in method signatures.

---

## Relationships

```
users 1 ──── * habits          (user_id FK, CASCADE)
habits 1 ──── * habit_logs     (habit_id FK, CASCADE)
habits 1 ──── 1 habit_streaks  (habit_id FK, CASCADE, UNIQUE)
users 1 ──── * habit_logs      (user_id FK, CASCADE)
```

---

## New Migrations

### V15 — Add target_days_of_week to habits

- Column: `target_days_of_week` JSONB, nullable
- Rollback: `<dropColumn>`
- No default value (existing habits are DAILY/WEEKLY, field is not applicable)

### V16 — Add reminder_time to habits

- Column: `reminder_time` TIME, nullable
- Rollback: `<dropColumn>`
- No default value (optional field)

---

## Indexes (existing from V4-V6)

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| habits | idx_habits_user_id | user_id | User data isolation queries |
| habit_logs | idx_habit_logs_habit_id | habit_id | Logs by habit lookup |
| habit_logs | idx_habit_logs_user_id | user_id | User data isolation queries |
| habit_streaks | idx_habit_streaks_habit_id | habit_id | Streak by habit lookup |

---

## Soft Delete Filtering

All user-facing queries on `habits` and `habit_logs` MUST include `WHERE deleted_at IS NULL`. The `habit_streaks` table has no soft delete (it is recalculated, not user-deleted).
