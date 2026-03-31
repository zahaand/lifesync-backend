# Feature Specification: Goals Feature

**Feature Branch**: `006-goals-feature`  
**Created**: 2026-03-31  
**Status**: Draft  
**Input**: User description: "Goals Feature — full CRUD, progress tracking, habit-goal linking, Kafka integration"

## Clarifications

### Session 2026-03-31

- Q: How is goal progress updated — automatically, manually, or both? → A: Both. Automatic via habit completion ratio + manual via PATCH endpoint. [RESOLVED]
- Q: Are milestone-based notifications in scope for Sprint 6? → A: No. Sprint 6 implements stub consumers only (GoalAnalyticsConsumer, GoalNotificationConsumer). Real notifications with thresholds (25%, 50%, 75%, 100%) deferred to Sprint 7. [RESOLVED]
- Q: How is automatic goal progress calculated from linked habits? → A: (completed habits count / total linked habits count) * 100, rounded to integer. If no habits linked, progress is manual-only. [RESOLVED]

## User Scenarios & Testing

### User Story 1 - Goal Management (Priority: P1)

A user creates a personal goal with a title, optional description, and optional target date. They can view all their active goals, view a single goal with full details, edit goal properties, and soft-delete goals they no longer want to track.

**Why this priority**: Goal management is the foundational capability. Without creating and viewing goals, no other goal feature can function. This is the minimum viable product for goals.

**Independent Test**: Can be fully tested by creating a goal, viewing the list, editing it, and deleting it. Delivers standalone value as a goal tracker even without progress or habit linking.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a goal with title "Run a marathon" and target date "2026-09-15", **Then** the goal is created with status ACTIVE, progress 0%, and appears in their goal list.
2. **Given** a user with 3 goals, **When** they request their goal list, **Then** they see all 3 goals with titles, statuses, and progress percentages (soft-deleted goals are excluded).
3. **Given** a user owns a goal, **When** they update the title to "Run a half-marathon", **Then** the goal reflects the new title and the updated timestamp changes.
4. **Given** a user owns a goal, **When** they delete it, **Then** the goal is soft-deleted (no longer appears in listings but data is retained).
5. **Given** a user tries to view or modify another user's goal, **Then** the system denies access.
6. **Given** a user creates a goal without a title, **Then** the system rejects the request with a validation error.

---

### User Story 2 - Milestone Management (Priority: P2)

A user breaks a goal into milestones (ordered sub-tasks) for organizational purposes. They can add, reorder, complete, uncomplete, and soft-delete milestones. Milestones provide structure and visual tracking within a goal but do not drive the goal's progress percentage.

**Why this priority**: Milestones give goals internal structure and allow users to break large objectives into manageable steps. They depend on goal CRUD (P1) but are independent from progress calculation.

**Independent Test**: Can be tested by creating a goal, adding milestones, reordering them, marking some complete, and verifying the milestone list updates correctly. Delivers value as an organizational tool within goals.

**Acceptance Scenarios**:

1. **Given** a goal with no milestones, **When** the user adds 4 milestones ("Register", "Train 10K", "Train 21K", "Race day"), **Then** the goal has 4 milestones in the specified order.
2. **Given** a goal with 4 milestones, **When** the user completes "Register", **Then** the milestone is marked as complete with a completion timestamp.
3. **Given** a goal with a completed milestone, **When** the user unchecks it, **Then** the milestone reverts to incomplete and the completion timestamp is cleared.
4. **Given** a user reorders milestones, **Then** the sort order updates accordingly.
5. **Given** a user soft-deletes a milestone, **Then** it no longer appears in the goal's milestone list but data is retained.
6. **Given** a user adds a milestone without a title, **Then** the system rejects the request with a validation error.

---

### User Story 3 - Habit-Goal Linking & Automatic Progress (Priority: P3)

A user links one or more of their existing habits to a goal. The system automatically calculates goal progress based on the ratio of completed linked habits to total linked habits. When any linked habit is completed, progress is recalculated and a GoalProgressUpdatedEvent is published. The user can also unlink habits, which triggers recalculation.

**Why this priority**: Linking habits to goals and automatic progress is the core differentiator of LifeSync — connecting daily actions to long-term aspirations with real-time feedback. It depends on goals (P1) and the existing habits feature.

**Independent Test**: Can be tested by linking habits to a goal, completing one habit, and verifying the goal progress updates to the correct percentage. Delivers value by automatically tracking goal completion based on daily habits.

**Acceptance Scenarios**:

1. **Given** a user has a goal "Run a marathon" and a habit "Morning run", **When** they link the habit to the goal, **Then** the habit appears in the goal's linked habits list.
2. **Given** a habit is already linked to a goal, **When** the user tries to link it again, **Then** the system rejects the duplicate with an appropriate error.
3. **Given** a goal has 2 linked habits, **When** the user unlinks one, **Then** only the remaining habit is shown and progress is recalculated.
4. **Given** a user tries to link another user's habit to their goal, **Then** the system denies access.
5. **Given** a habit is deleted (soft-deleted), **Then** it is automatically unlinked from all goals via database cascade.
6. **Given** a goal with 4 linked habits (2 completed today), **When** the user views the goal, **Then** progress shows 50%.
7. **Given** a goal with 3 linked habits (all completed today), **Then** progress is 100% and the status changes to COMPLETED.
8. **Given** a goal with no linked habits, **Then** automatic progress calculation does not apply; progress is controlled only via manual update.

---

### User Story 4 - Manual Goal Progress Update (Priority: P4)

A user directly sets a goal's progress percentage (0-100) via a dedicated endpoint. This is useful for goals without linked habits, or when the user wants to override the automatic calculation. Both manual and automatic updates write to the same progress field on the goal.

**Why this priority**: Manual progress ensures all goals can be tracked, even those without linked habits. It provides flexibility and user control. Depends on goal CRUD (P1).

**Independent Test**: Can be tested by creating a goal with no linked habits, manually setting progress to 50%, verifying the update, then setting to 100% and verifying status changes to COMPLETED.

**Acceptance Scenarios**:

1. **Given** a goal with no linked habits and progress 0%, **When** the user sets progress to 50%, **Then** the goal progress updates to 50% and a GoalProgressUpdatedEvent is published.
2. **Given** a goal with progress 75%, **When** the user sets progress to 100%, **Then** the goal status changes to COMPLETED and a GoalProgressUpdatedEvent is published.
3. **Given** a user sets progress to a value outside 0-100, **Then** the system rejects the request with a validation error.
4. **Given** a user tries to update progress on another user's goal, **Then** the system denies access.
5. **Given** a goal with linked habits, **When** the user manually sets progress, **Then** the manual value takes effect (user override). The next automatic recalculation from a habit completion will overwrite the manual value.

---

### User Story 5 - Automatic Progress via Habit Completion Events (Priority: P5)

When a user completes a habit that is linked to one or more goals, a consumer processes the HabitCompletedEvent, recalculates goal progress for each linked active goal, and publishes a GoalProgressUpdatedEvent per goal. This is the async integration between the habits and goals features.

**Why this priority**: This is the event-driven backbone. It depends on habit-goal linking (P3) and reuses the existing Kafka infrastructure. It completes the automatic progress feedback loop.

**Independent Test**: Can be tested by completing a habit linked to a goal and verifying that a GoalProgressUpdatedEvent is published with the correct recalculated progress percentage.

**Acceptance Scenarios**:

1. **Given** a habit linked to a goal with 4 linked habits (1 previously completed), **When** the user completes this habit, **Then** goal progress is recalculated to 50% and a GoalProgressUpdatedEvent is published.
2. **Given** a habit linked to multiple goals, **When** the user completes the habit, **Then** a separate GoalProgressUpdatedEvent is published for each linked active goal.
3. **Given** a habit not linked to any goal, **When** the user completes it, **Then** no GoalProgressUpdatedEvent is published.
4. **Given** the same habit completion event is received twice (duplicate), **Then** the consumer processes it only once (idempotency).
5. **Given** a habit linked to a soft-deleted goal, **When** the user completes it, **Then** no GoalProgressUpdatedEvent is published for the deleted goal.

---

### User Story 6 - Goal Progress Event Consumers (Priority: P6)

When a GoalProgressUpdatedEvent is published, two stub consumers process it: GoalAnalyticsConsumer (logs an analytics update placeholder) and GoalNotificationConsumer (logs a notification placeholder). Both consumers are idempotent and use the dead-letter queue for failed messages. Real notification implementation with milestone thresholds (25%, 50%, 75%, 100%) is deferred to Sprint 7.

**Why this priority**: These are infrastructure stubs that complete the event-driven pipeline. They establish the consumer pattern for future features without blocking the current sprint.

**Independent Test**: Can be tested by publishing a GoalProgressUpdatedEvent to Kafka and verifying that both consumers process it, log the expected messages, and record the event as processed for idempotency.

**Acceptance Scenarios**:

1. **Given** a GoalProgressUpdatedEvent is published, **When** GoalAnalyticsConsumer receives it, **Then** it logs the goal ID and progress percentage at INFO level and marks the event as processed.
2. **Given** a GoalProgressUpdatedEvent is published, **When** GoalNotificationConsumer receives it, **Then** it logs the notification placeholder at INFO level and marks the event as processed.
3. **Given** a duplicate event is received by either consumer, **Then** the consumer skips processing and logs a WARN.
4. **Given** a consumer fails to process an event after retries, **Then** the event is sent to the goal.progress.updated.dlq topic.

---

### Edge Cases

- What happens when a user deletes a goal that has milestones and linked habits? All milestones are soft-deleted with the goal; habit links are removed via database cascade.
- What happens when a user completes a habit linked to a soft-deleted goal? No GoalProgressUpdatedEvent is published for deleted goals.
- What happens when a user sets a target date in the past? The system allows it (goals can be retroactive records).
- What happens when all habits are unlinked from a goal? Progress retains its last value; future updates are manual-only until new habits are linked.
- What happens when a milestone is completed on a goal with status COMPLETED? The milestone is updated but goal status remains COMPLETED.
- What happens when a user manually sets progress and then a linked habit is completed? The automatic recalculation overwrites the manual value.
- What happens when a goal has linked habits but none are completed? Progress is 0%.
- What happens when progress reaches 100% via either manual or automatic update? Goal status changes to COMPLETED regardless of update source.

## Requirements

### Functional Requirements

- **FR-001**: System MUST allow authenticated users to create goals with a required title, optional description, and optional target date.
- **FR-002**: System MUST allow users to view a list of their own active (non-deleted) goals, including title, status, progress percentage, and target date.
- **FR-003**: System MUST allow users to view a single goal with full details including milestones and linked habits.
- **FR-004**: System MUST allow users to update a goal's title, description, target date, and status.
- **FR-005**: System MUST allow users to soft-delete goals (set deleted_at timestamp, exclude from listings).
- **FR-006**: System MUST enforce user data isolation — users can only access and modify their own goals.
- **FR-007**: System MUST allow users to add milestones to a goal with a title and sort order.
- **FR-008**: System MUST allow users to mark milestones as complete or incomplete.
- **FR-009**: System MUST allow users to reorder milestones within a goal.
- **FR-010**: System MUST allow users to soft-delete milestones from a goal.
- **FR-011**: System MUST allow users to link their own habits to their own goals (many-to-many relationship).
- **FR-012**: System MUST prevent duplicate habit-goal links.
- **FR-013**: System MUST allow users to unlink habits from goals.
- **FR-014**: System MUST automatically recalculate goal progress as (completed linked habits count / total linked habits count) * 100, rounded to the nearest integer, whenever a linked habit is completed.
- **FR-015**: System MUST allow users to manually set goal progress (0-100) via a dedicated endpoint.
- **FR-016**: System MUST automatically set goal status to COMPLETED when progress reaches 100% (via either automatic or manual update).
- **FR-017**: System MUST publish a GoalProgressUpdatedEvent when goal progress changes — whether from automatic habit-based recalculation or manual update.
- **FR-018**: System MUST publish separate GoalProgressUpdatedEvents for each active goal linked to a completed habit.
- **FR-019**: System MUST provide a GoalAnalyticsConsumer stub that processes GoalProgressUpdatedEvents idempotently.
- **FR-020**: System MUST provide a GoalNotificationConsumer stub that processes GoalProgressUpdatedEvents idempotently.
- **FR-021**: System MUST route failed GoalProgressUpdatedEvents to the dead-letter queue after retry exhaustion.

### Key Entities

- **Goal**: A user's long-term objective. Has a title, optional description, optional target date, status (ACTIVE/COMPLETED), progress percentage (0-100), and soft-delete support. Owned by exactly one user. Progress is updated via two paths: automatically from linked habit completions, or manually by the user.
- **Goal Milestone**: An ordered sub-task within a goal for organizational purposes. Has a title, sort order, completion status, and soft-delete support. Milestones do not affect the goal's progress percentage.
- **Goal-Habit Link**: A many-to-many relationship between goals and habits via a junction table. Progress is derived from the ratio of completed linked habits to total linked habits. When any linked habit is completed, goal progress is recalculated and an event is published. A habit can be linked to multiple goals, and a goal can have multiple linked habits.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can create, view, update, and delete goals within 2 seconds per operation under normal conditions.
- **SC-002**: Users can manually set goal progress and see the update reflected immediately (within 1 second).
- **SC-003**: When a linked habit is completed, goal progress is recalculated and the corresponding GoalProgressUpdatedEvent is published within 5 seconds.
- **SC-004**: Duplicate events are detected and skipped by consumers 100% of the time (zero duplicate processing).
- **SC-005**: Failed events are routed to the dead-letter queue after retry exhaustion with no message loss.
- **SC-006**: Users cannot access or modify any goal, milestone, or habit-goal link belonging to another user (100% data isolation).
- **SC-007**: All goal-related operations are documented and testable via the interactive documentation interface.

## Assumptions

- Users are already authenticated via the existing JWT-based authentication system (Sprint 2).
- The habits feature (Sprint 4) is fully functional and habits can be referenced by ID.
- The existing Kafka infrastructure (Sprint 5) — topic configuration, consumer error handling, DLQ routing, idempotency via processed_events table — is reused without modification.
- Database migrations for goals, milestones, and goal-habits are already applied (V7, V8, V9) and do not need changes.
- Goal status transitions are limited to ACTIVE and COMPLETED. Additional statuses (PAUSED, ABANDONED) are out of scope for this sprint.
- Progress calculation from linked habits uses simple ratio (completed/total), rounded to integer. Weighted habits are out of scope.
- Milestones are organizational sub-tasks only; they do not affect goal progress percentage.
- Manual progress updates are overwritten by the next automatic recalculation when a linked habit is completed.
- Real milestone-based notifications (25%, 50%, 75%, 100% thresholds) are deferred to Sprint 7. Sprint 6 implements stub consumers only.
- Reminders and scheduled notifications are out of scope (Sprint 7).
