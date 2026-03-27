# Data Model: Foundation Infrastructure

**Date**: 2026-03-27
**Feature**: 001-foundation-infra

## Legend

- **PK**: Primary key (UUID, default `gen_random_uuid()`)
- **FK**: Foreign key
- **NN**: NOT NULL
- **UQ**: Unique constraint
- **SI**: Soft-delete indicator (`deleted_at TIMESTAMPTZ`)
- **AUD**: Audit columns (`created_at TIMESTAMPTZ NN DEFAULT now()`,
  `updated_at TIMESTAMPTZ NN DEFAULT now()`)

---

## Domain: user

### users

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| email | VARCHAR(255) | NN, UQ | Login identifier |
| password_hash | VARCHAR(255) | NN | Bcrypt hash |
| username | VARCHAR(100) | NN, UQ | Display handle |
| enabled | BOOLEAN | NN, DEFAULT true | Account active flag |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

### user_profiles

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| user_id | UUID | FK → users(id), NN, UQ | One-to-one |
| display_name | VARCHAR(150) | NULL | Preferred name |
| timezone | VARCHAR(50) | NN, DEFAULT 'UTC' | IANA tz |
| locale | VARCHAR(10) | NN, DEFAULT 'en' | Preferred locale |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |

### refresh_tokens

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| user_id | UUID | FK → users(id), NN | Token owner |
| token_hash | VARCHAR(255) | NN, UQ | SHA-256 of token |
| expires_at | TIMESTAMPTZ | NN | Expiration time |
| revoked | BOOLEAN | NN, DEFAULT false | Revocation flag |

No audit columns (per clarification).

---

## Domain: habit

### habits

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| user_id | UUID | FK → users(id), NN | Owner |
| name | VARCHAR(200) | NN | Habit title |
| description | TEXT | NULL | Optional detail |
| frequency | VARCHAR(20) | NN | e.g. DAILY, WEEKLY |
| active | BOOLEAN | NN, DEFAULT true | Currently tracked |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

### habit_logs

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| habit_id | UUID | FK → habits(id), NN | Parent habit |
| user_id | UUID | FK → users(id), NN | Owner (denorm for isolation) |
| log_date | DATE | NN | Completion date |
| note | TEXT | NULL | Optional note |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

**Unique constraint**: (habit_id, log_date) — one log per habit per day.

### habit_streaks

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| habit_id | UUID | FK → habits(id), NN, UQ | One streak record per habit |
| current_streak | INTEGER | NN, DEFAULT 0 | Consecutive days |
| longest_streak | INTEGER | NN, DEFAULT 0 | All-time max |
| last_log_date | DATE | NULL | Last completion date |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |

---

## Domain: goal

### goals

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| user_id | UUID | FK → users(id), NN | Owner |
| title | VARCHAR(200) | NN | Goal title |
| description | TEXT | NULL | Optional detail |
| target_date | DATE | NULL | Deadline |
| status | VARCHAR(20) | NN, DEFAULT 'ACTIVE' | ACTIVE, COMPLETED, ABANDONED |
| progress | INTEGER | NN, DEFAULT 0 | 0-100 percentage |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

### goal_milestones

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| goal_id | UUID | FK → goals(id), NN | Parent goal |
| title | VARCHAR(200) | NN | Milestone title |
| sort_order | INTEGER | NN, DEFAULT 0 | Display order |
| completed | BOOLEAN | NN, DEFAULT false | Completion flag |
| completed_at | TIMESTAMPTZ | NULL | When completed |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

### goal_habits

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| goal_id | UUID | FK → goals(id), NN | Parent goal |
| habit_id | UUID | FK → habits(id), NN | Linked habit |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |

**Unique constraint**: (goal_id, habit_id) — no duplicate links.

---

## Domain: system

### notification_logs

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| user_id | UUID | FK → users(id), NN | Recipient |
| channel | VARCHAR(30) | NN | e.g. TELEGRAM, EMAIL |
| event_type | VARCHAR(100) | NN | What triggered it |
| status | VARCHAR(20) | NN | SENT, FAILED, PENDING |
| message | TEXT | NULL | Content sent |
| sent_at | TIMESTAMPTZ | NULL | Delivery time |
| created_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| updated_at | TIMESTAMPTZ | NN, DEFAULT now() | AUD |
| deleted_at | TIMESTAMPTZ | NULL | SI |

### processed_events

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | gen_random_uuid() |
| event_id | VARCHAR(255) | NN, UQ | Idempotency key |
| event_type | VARCHAR(100) | NN | Event classification |
| processed_at | TIMESTAMPTZ | NN, DEFAULT now() | When consumed |

No audit columns, no soft delete (per clarification).

---

## Entity Relationship Summary

```text
users 1──1 user_profiles
users 1──* habits
users 1──* goals
users 1──* refresh_tokens
users 1──* notification_logs
habits 1──* habit_logs
habits 1──1 habit_streaks
habits *──* goals        (via goal_habits)
goals  1──* goal_milestones
```

## Index Strategy (foundation)

- All foreign key columns get implicit indexes via FK constraints in PostgreSQL.
- Explicit indexes added during migration:
  - `users(email)` — unique index (login lookup)
  - `users(username)` — unique index
  - `habit_logs(habit_id, log_date)` — unique composite (one log per day)
  - `goal_habits(goal_id, habit_id)` — unique composite
  - `processed_events(event_id)` — unique index (idempotency lookup)
  - `refresh_tokens(token_hash)` — unique index (token validation)
