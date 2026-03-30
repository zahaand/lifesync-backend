# Feature Specification: Habits Core

**Feature Branch**: `004-habits-core`
**Created**: 2026-03-30
**Status**: Draft
**Input**: User description: "Sprint 4 — Habits Core: CRUD, completion logs, streak calculation, jOOQ codegen"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Manage Habits (Priority: P1)

As an authenticated user, I want to create habits with a title, optional description, frequency (daily, weekly, or custom days of the week), and optional reminder time, so that I can define what I want to track.

I can view a paginated list of my habits filtered by active or inactive status, view details of a specific habit, update any habit property, and soft-delete habits I no longer need. I can never see or modify other users' habits.

**Why this priority**: Without habits, no other feature in this sprint has purpose. This is the foundational data model that completion logs and streaks depend on.

**Independent Test**: Can be fully tested by creating a habit, listing it, updating it, and soft-deleting it. Delivers value as a standalone habit catalog.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a habit with title "Morning run", frequency "DAILY", **Then** the habit is created with a unique ID, is active by default, and appears in their habit list.
2. **Given** a user with 25 habits, **When** they list habits with page=0, size=10, **Then** they receive the first 10 habits with pagination metadata (total elements, total pages).
3. **Given** a user with active and inactive habits, **When** they list habits filtered by status "active", **Then** only active habits are returned.
4. **Given** a user owns a habit, **When** they update its title and set frequency to "CUSTOM" with target days [MONDAY, WEDNESDAY, FRIDAY], **Then** the habit is updated and the response reflects the new values.
5. **Given** a user owns a habit, **When** they delete it, **Then** the habit is soft-deleted (no longer appears in list) but data is retained in the system.
6. **Given** a user does NOT own a habit, **When** they attempt to view, update, or delete it, **Then** they receive a 404 Not Found error (no information leakage about other users' data).

---

### User Story 2 - Log Habit Completions (Priority: P1)

As a user, I want to log that I completed a habit on a specific date with an optional note, so that I can track my progress over time. I can also remove a log entry if I made a mistake. I can view a paginated history of my completions for any habit.

**Why this priority**: Completion logging is the core interaction loop — without it, habits are just a static list with no tracking value.

**Independent Test**: Can be tested by creating a habit, logging a completion for today, viewing the log list, and deleting the log entry. Delivers value as a daily tracking journal.

**Acceptance Scenarios**:

1. **Given** a user owns an active habit, **When** they log a completion for today with note "5km", **Then** a log entry is created with the habit ID, date, and note.
2. **Given** a log entry already exists for a habit on a specific date, **When** the user attempts to log another completion for the same date, **Then** the system rejects the request with a conflict error.
3. **Given** a user has logged completions for a habit, **When** they view the completion list, **Then** they receive a paginated list of log entries sorted by date descending.
4. **Given** a user owns a log entry, **When** they delete it, **Then** the log entry is soft-deleted (excluded from queries, data retained).
5. **Given** a user does NOT own the habit, **When** they attempt to log a completion, **Then** they receive a 404 error.

---

### User Story 3 - Track Habit Streaks (Priority: P2)

As a user, I want to see my current streak (consecutive days of completion) and my longest-ever streak for each habit, so that I can stay motivated and measure consistency.

**Why this priority**: Streaks add motivational value but depend on completions being implemented first. The feature is read-only from the user's perspective — streaks are recalculated automatically when completions are logged or removed.

**Independent Test**: Can be tested by logging completions for consecutive days, querying streak data, then breaking the streak and verifying it resets. Delivers value as a motivational progress indicator.

**Acceptance Scenarios**:

1. **Given** a user logs a completion for a DAILY habit today and has logged every day for the past 5 days, **When** they view the streak, **Then** current streak is 6 and longest streak is at least 6.
2. **Given** a user has a DAILY habit with a last log date 2 or more days ago, **When** they view the streak, **Then** current streak is 0 (streak has been broken).
3. **Given** a user has a DAILY habit with a last log date of yesterday, **When** they view the streak, **Then** current streak shows the consecutive count (streak is still alive).
4. **Given** a user's current streak of 10 was previously their longest, and their streak resets to 0, **When** they view the streak, **Then** longest streak remains 10.
5. **Given** a user deletes a completion log that was part of the current streak, **When** they view the streak, **Then** the streak is recalculated to reflect the gap.

---

### User Story 4 - Configure jOOQ Code Generation (Priority: P1)

As a developer, I want the database access layer to use generated type-safe classes from jOOQ instead of string-based DSL, so that the codebase is less error-prone and refactor-safe. Existing repositories for users and refresh tokens should also be migrated to use generated classes.

**Why this priority**: This is a technical prerequisite — all new repositories for habits, logs, and streaks must be built on generated jOOQ classes. Migrating existing repositories prevents two competing access patterns.

**Independent Test**: Can be validated by running jOOQ code generation against the running database and confirming that all tables produce generated classes. Existing tests for user and refresh token repositories must still pass after migration.

**Acceptance Scenarios**:

1. **Given** the database is running via Docker Compose, **When** a developer runs the jOOQ code generation build step, **Then** type-safe classes are generated for all database tables.
2. **Given** the existing user and refresh token repositories use string-based DSL, **When** they are migrated to generated jOOQ classes, **Then** all existing tests pass without modification.

---

### Edge Cases

- What happens when a user creates a habit with CUSTOM frequency but provides no target days of week? The system rejects the request with a validation error.
- What happens when a user creates a habit with DAILY or WEEKLY frequency and also provides target days of week? The system ignores the target days field (only relevant for CUSTOM).
- What happens when a user logs a completion for a date in the far future? The system accepts it — users may want to pre-log planned activities.
- What happens when a user logs a completion for an inactive habit? The system rejects the request — the user must reactivate the habit first.
- What happens when a user logs a completion for a soft-deleted habit? The system rejects the request because the habit no longer exists from the user's perspective.
- What happens when a user views the streak of a habit with zero completions? The system returns current streak = 0, longest streak = 0, and no last log date.
- How are streaks calculated for WEEKLY habits? Current streak counts consecutive weeks with at least one completion.
- How are streaks calculated for CUSTOM habits? Current streak counts consecutive target days that were completed (e.g., if target is Mon/Wed/Fri, missing a Tuesday does not break the streak).

## Clarifications

### Session 2026-03-30

- Q: Should completion log deletion be soft delete or hard delete? → A: Soft delete (set `deleted_at`, exclude from queries, retain data).
- Q: Can users log completions for inactive (but not deleted) habits? → A: No — completions only allowed for active habits.
- Q: Are soft-deleted habit logs included in streak calculations? → A: No — soft-deleted logs (deleted_at IS NOT NULL) are excluded from all streak calculations. Streak recalculates as if those logs never existed.
- Q: What are the PATCH semantics for updating a habit? → A: PATCH /habits/{id} follows JSON Merge Patch semantics (RFC 7396): field present with value → update to that value; field present as null → clear the field (set to null/default); field absent → leave unchanged. Applies to: description, reminderTime, targetDaysOfWeek.
- Q: What happens to streak when habit frequency is changed? → A: Streak is reset to 0 and recalculated from scratch using the new frequency logic. Historical logs are preserved.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-000** (Prerequisite): All existing Sprint 2 controllers MUST be updated to use `/api/v1` prefix before implementing any new habits endpoints:
  - AuthController: `/auth/**` → `/api/v1/auth/**`
  - UserController: `/users/**` → `/api/v1/users/**`
  - AdminController: `/admin/**` → `/api/v1/admin/**`
  - Update SecurityConfig permit rules accordingly.
  - Update OpenAPI YAML server URL to reflect `/api/v1` base path.
  - This ensures all 20 endpoints (11 existing + 9 new) share the same base path.
- **FR-001**: System MUST allow authenticated users to create habits with a title (required, max 200 characters), description (optional), frequency (DAILY, WEEKLY, or CUSTOM), target days of week (required for CUSTOM frequency, stored as a list of day names), and reminder time (optional).
- **FR-002**: System MUST return a paginated list of the user's own habits, sortable by creation date, filterable by active/inactive status.
- **FR-003**: System MUST allow users to retrieve a single habit by ID, returning full details including all fields.
- **FR-004**: System MUST allow users to update any mutable habit property: title, description, frequency, target days of week, reminder time, and active status.
- **FR-005**: System MUST soft-delete habits by setting a deletion timestamp, excluding them from list results while retaining data.
- **FR-006**: System MUST enforce user data isolation — users can only access, modify, or delete their own habits. Attempts to access another user's habit MUST return 404 (not 403).
- **FR-007**: System MUST allow users to log a completion for a specific **active** habit and date, with an optional text note. Completions for inactive or soft-deleted habits MUST be rejected.
- **FR-008**: System MUST enforce one completion per habit per date — duplicate attempts MUST be rejected with a conflict error.
- **FR-009**: System MUST soft-delete completion log entries by setting a deletion timestamp, excluding them from user-facing queries while retaining data.
- **FR-010**: System MUST return a paginated list of completion log entries for a specific habit, sorted by date descending.
- **FR-011**: System MUST automatically recalculate streak data (current streak, longest streak, last log date) after each completion is logged or deleted.
- **FR-012**: System MUST calculate current streak as the count of consecutive completion days ending on today or yesterday (for DAILY habits). If the last log date is more than 1 day ago, current streak MUST be 0.
- **FR-013**: System MUST track the longest-ever streak and never decrease it unless the underlying log data changes.
- **FR-014**: System MUST expose a streak endpoint returning current streak, longest streak, and last log date for a given habit.
- **FR-015**: System MUST use generated type-safe jOOQ classes for all database access in new and existing repositories.
- **FR-016**: System MUST validate that CUSTOM frequency habits include at least one target day of week; DAILY and WEEKLY habits MUST NOT require target days.

### Key Entities

- **Habit**: Represents a trackable behavior belonging to a user. Key attributes: title, description, frequency, target days of week, reminder time, active status. Supports soft deletion.
- **Habit Log**: Represents a single completion event for a habit on a specific date. Key attributes: habit reference, date, optional note. Unique per habit per date.
- **Habit Streak**: Represents aggregated streak data for a habit. Key attributes: current streak count, longest streak count, last log date. One-to-one relationship with habit.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a habit and see it in their list within 2 seconds of submission.
- **SC-002**: Users can log a daily habit completion in a single action (one request).
- **SC-003**: Streak data is accurate within 1 second of a completion being logged or removed.
- **SC-004**: Habit list supports at least 100 habits per user without noticeable performance degradation.
- **SC-005**: All habit operations enforce user isolation — no user can access another user's habits under any circumstance.
- **SC-006**: 100% of existing user and refresh token repository tests pass after migration to generated jOOQ classes.

## Assumptions

- Users have already authenticated via the existing auth system (Sprint 2) and possess a valid JWT token.
- The database schema for habits, habit_logs, and habit_streaks already exists (Liquibase migrations V4-V6 are in place).
- The existing database schema uses "name" for the habit title field — the API will present this as "title" to users.
- The existing habits table does not have columns for `target_days_of_week` or `reminder_time` — new database migrations will be needed to add these fields.
- Streak calculation for WEEKLY and CUSTOM frequencies uses a week-based or target-day-based consecutive count respectively, not a strict daily count.
- All time-sensitive operations use an injected Clock instance, never system time directly, to support deterministic testing.
- PostgreSQL must be running (via Docker Compose) for jOOQ code generation, as the plugin connects to a live database to introspect the schema.
- All endpoints across all sprints use `/api/v1` base path. Sprint 2 controllers will be updated as part of this sprint prerequisite.
- Soft-deleted habits are excluded from all user-facing queries but remain in the database for data integrity and potential future restoration.
