# Feature Specification: Reminders & Notifications

**Feature Branch**: `007-reminders-notifications`
**Created**: 2026-03-31
**Status**: Draft
**Input**: User description: "Sprint 7 — Reminders & Notifications: scheduled habit reminders via Telegram, goal progress milestone notifications, user timezone support"

## Clarifications

### Session 2026-03-31

- Q: Is TelegramNotificationConsumer a stub? -> A: No. It already has full streak milestone logic (7/14/21/30/60/90 days) implemented in Sprint 5. No replacement needed — only message enhancement. [RESOLVED]
- Q: Does user timezone already exist? -> A: Yes. `UserProfile` already has a `timezone` field (defaults to "UTC") and a `withTimezone()` method. No new schema needed for timezone storage. [RESOLVED]
- Q: What is the reminder scheduling mechanism? -> A: A scheduled job runs every minute (cron = "0 * * * * *"). Each run finds all active habits whose reminder_time matches the current minute in the user's timezone. Accuracy: ±1 minute. [RESOLVED]
- Q: What prevents duplicate reminders for the same habit on the same day? -> A: A `sent_reminders` table with columns (habit_id UUID FK, user_id UUID FK, sent_date date) and composite unique constraint on (habit_id, sent_date). Before sending: check if row exists for today; if yes → skip; if no → send + insert. Survives app restarts. [RESOLVED]
- Q: Should habit milestone notifications be enhanced in Sprint 7? -> A: The existing TelegramNotificationConsumer already sends streak milestone notifications. Sprint 7 enhances the message to include the habit title, making notifications more personal. No structural change needed. [RESOLVED]
- Q: What is the exact habit streak milestone message format? -> A: Enhanced from "You've reached a {N}-day streak! Keep going!" to "🔥 {habitTitle}: {N}-day streak! Keep going!" [RESOLVED]
- Q: What are the exact goal milestone notification message formats? -> A: Standard: "🎯 {goalTitle}: {N}% complete! Keep going!" Special for 100%: "🎯 {goalTitle}: Goal achieved! Congratulations! 🎉" Consumer must check user.telegramChatId and lifesync.telegram.enabled flag before sending. [RESOLVED]
- Q: How does the scheduler handle timezone conversion? -> A: Scheduler runs in UTC. For each habit: load user's timezone from profile (default: UTC), convert current UTC time to user's local time, compare with habit.reminderTime. If match (within current minute) → send reminder. [RESOLVED]

## User Scenarios & Testing

### User Story 1 - Habit Reminders via Telegram (Priority: P1)

A user sets a reminder time on a habit (e.g., "08:00"). Each day at that time in the user's local timezone, the system sends a Telegram message reminding them to complete the habit. The scheduler runs every minute in UTC, converts the current time to each user's local timezone, and matches against the habit's reminder_time. Reminders are only sent for active (non-deleted) habits where the user has connected their Telegram account. If the user has already completed the habit for today, no reminder is sent. Duplicate prevention is handled via a `sent_reminders` table with a composite unique constraint on (habit_id, sent_date).

**Why this priority**: Habit reminders are the core value of this sprint — they provide proactive, personalized nudges that drive habit consistency. Without reminders, users must remember to check the app on their own.

**Independent Test**: Can be fully tested by creating a habit with a reminder time, connecting Telegram, and verifying that a notification arrives at the specified time. Delivers standalone value as a daily reminder system.

**Acceptance Scenarios**:

1. **Given** a user has Telegram connected and a habit "Morning run" with reminder_time "07:00" and timezone "Europe/Moscow", **When** the scheduler runs and 07:00 MSK matches the current minute, **Then** the system sends a Telegram message: "⏰ Time to Morning run! Don't break your streak!"
2. **Given** a user has Telegram connected and a habit with reminder_time "08:00", **When** the user has already completed the habit today, **Then** no reminder is sent.
3. **Given** a user has Telegram connected and two habits with reminder_time "09:00", **When** the scheduler runs at 09:00 in the user's timezone, **Then** both habits trigger separate reminder messages.
4. **Given** a habit has reminder_time set but the user has NOT connected Telegram (no telegramChatId), **Then** no reminder is sent and no error occurs.
5. **Given** a habit has reminder_time set to null, **Then** no reminder is scheduled for that habit.
6. **Given** a habit is soft-deleted, **Then** no reminder is sent even if reminder_time was set.
7. **Given** the same reminder was already sent for a habit today (row exists in sent_reminders for today's date), **When** the scheduled job runs again, **Then** no duplicate reminder is sent.
8. **Given** Telegram integration is disabled via the config flag, **Then** no reminders are sent and no errors are logged.

---

### User Story 2 - Goal Progress Milestone Notifications (Priority: P2)

When a user's goal progress reaches a milestone threshold (25%, 50%, 75%, or 100%), the system sends a Telegram congratulations message. Each milestone is sent at most once per goal — reaching 50% twice (e.g., after manual override and recalculation) does not trigger a second notification. The GoalNotificationConsumer (currently a stub) is replaced with real notification logic that checks user.telegramChatId and the lifesync.telegram.enabled flag before sending.

**Why this priority**: Goal milestones provide motivational feedback at key progress points. This replaces the existing GoalNotificationConsumer stub with real notification logic, completing the goal feature's event-driven pipeline.

**Independent Test**: Can be tested by updating a goal's progress to 25%, 50%, 75%, and 100% and verifying that a Telegram notification is sent at each threshold exactly once.

**Acceptance Scenarios**:

1. **Given** a goal at 20% progress and the user has Telegram connected, **When** progress updates to 25%, **Then** a message is sent: "🎯 Run a marathon: 25% complete! Keep going!"
2. **Given** a goal at 49% progress, **When** progress updates to 60% (skipping 50%), **Then** the 50% milestone message is sent (since 50% was crossed): "🎯 Run a marathon: 50% complete! Keep going!"
3. **Given** a goal that already received the 50% milestone notification, **When** progress drops to 40% and then returns to 50%, **Then** no duplicate 50% notification is sent.
4. **Given** a goal reaches 100%, **Then** a special completion message is sent: "🎯 Run a marathon: Goal achieved! Congratulations! 🎉"
5. **Given** the user has NOT connected Telegram, **When** a milestone is reached, **Then** no notification is sent and no error occurs.
6. **Given** Telegram integration is disabled, **Then** no milestone notifications are sent.
7. **Given** the same GoalProgressUpdatedEvent is received twice (duplicate), **Then** the consumer processes it only once (existing idempotency).
8. **Given** a goal's progress jumps from 10% to 80% in one update, **Then** notifications for 25%, 50%, and 75% milestones are all sent.

---

### User Story 3 - Habit Streak Milestone Notification Enhancement (Priority: P3)

The existing streak milestone notifications (7, 14, 21, 30, 60, 90 days) are enhanced to include the habit title in the message, making notifications more personal and actionable. The message format changes from "You've reached a {N}-day streak! Keep going!" to "🔥 {habitTitle}: {N}-day streak! Keep going!"

**Why this priority**: The existing consumer already sends streak notifications. This story enhances message quality without structural change. Lower priority because the feature already works — this is an improvement.

**Independent Test**: Can be tested by completing a habit to reach a 7-day streak and verifying the notification includes the habit title.

**Acceptance Scenarios**:

1. **Given** a user reaches a 7-day streak on habit "Morning run", **When** the notification is sent, **Then** the message reads: "🔥 Morning run: 7-day streak! Keep going!"
2. **Given** a user reaches a 30-day streak on habit "Read 30 min", **Then** the message reads: "🔥 Read 30 min: 30-day streak! Keep going!"
3. **Given** a streak that is not a milestone (e.g., 8 days), **Then** no notification is sent (existing behavior preserved).

---

### User Story 4 - Timezone-Aware Reminder Scheduling (Priority: P4)

The scheduler runs in UTC. For each habit with a reminder_time, it loads the user's timezone from their profile (defaults to "UTC"), converts the current UTC time to the user's local time, and compares with the habit's reminder_time. If the local time matches the current minute, the reminder is sent. The timezone field already exists on the user profile and can be updated via the existing profile API.

**Why this priority**: Timezone support is critical for reminders (US1) but is a cross-cutting concern. It depends on the reminder infrastructure being in place. The timezone field already exists; this story ensures it is used correctly in scheduling.

**Independent Test**: Can be tested by setting a user's timezone to a non-UTC zone (e.g., "Asia/Tokyo"), creating a habit with reminder_time "09:00", and verifying the reminder fires at 09:00 JST, not 09:00 UTC.

**Acceptance Scenarios**:

1. **Given** a user with timezone "America/New_York" and a habit with reminder_time "08:00", **When** the scheduler converts UTC to EST and 08:00 EST matches, **Then** the reminder is sent (not at 08:00 UTC).
2. **Given** a user with timezone "UTC" (default), **When** reminder_time is "10:00", **Then** the reminder fires at 10:00 UTC.
3. **Given** a user updates their timezone from "UTC" to "Europe/Berlin", **Then** future reminders use the new timezone.
4. **Given** a user has an invalid timezone string, **Then** the system falls back to UTC and logs a warning.

---

### Edge Cases

- What happens when the scheduled job takes longer than its interval to complete? The job must be non-overlapping — a new run does not start until the previous one finishes.
- What happens when the Telegram API is temporarily unavailable? The reminder attempt is logged as a failure; the system does not retry the same reminder (it is not resent until the next day). No crash or exception propagation.
- What happens when a user changes their habit's reminder_time mid-day? The new time takes effect on the next scheduled check. If the old time already passed and the reminder was sent, no duplicate is sent (sent_reminders row exists). If the new time hasn't passed yet, the reminder fires at the new time.
- What happens when a goal's progress jumps from 10% to 80% in one update? All crossed milestones (25%, 50%, 75%) trigger notifications.
- What happens when a habit is completed between the scheduler finding it and sending the notification? The system checks completion status before sending; if completed, the reminder is skipped.
- What happens when Daylight Saving Time shifts the user's local time? The scheduler always uses the current timezone rules (via standard timezone libraries), so DST transitions are handled automatically.
- What happens when the goal notification consumer receives an event for a soft-deleted goal? No notification is sent.
- What happens if goal milestones need to be tracked across restarts? A persistent record of sent milestones per goal ensures no duplicates even after application restart.
- What happens when the app restarts mid-day — are reminders re-sent? No. The sent_reminders table persists across restarts; the unique constraint on (habit_id, sent_date) prevents duplicates.

## Requirements

### Functional Requirements

- **FR-001**: System MUST send a Telegram reminder for each active habit with a non-null reminder_time when the scheduler detects a match between the current minute (in the user's local timezone) and the habit's reminder_time. The scheduler runs every minute with ±1 minute accuracy.
- **FR-002**: System MUST NOT send reminders for habits where the user has not connected Telegram (no telegramChatId).
- **FR-003**: System MUST NOT send reminders for habits the user has already completed today (in the user's local date).
- **FR-004**: System MUST NOT send duplicate reminders for the same habit on the same calendar day (user's local date). Duplicate prevention is enforced via a `sent_reminders` table with a composite unique constraint on (habit_id, sent_date). Before sending: check if row exists; if yes, skip.
- **FR-005**: System MUST NOT send reminders when the Telegram integration flag is disabled.
- **FR-006**: System MUST NOT send reminders for soft-deleted habits.
- **FR-007**: System MUST send a Telegram notification when a goal's progress crosses a milestone threshold (25%, 50%, 75%, 100%). Message format: "🎯 {goalTitle}: {N}% complete! Keep going!" Special for 100%: "🎯 {goalTitle}: Goal achieved! Congratulations! 🎉"
- **FR-008**: System MUST send notifications for ALL milestones crossed in a single progress update (e.g., 10% -> 80% triggers 25%, 50%, 75%).
- **FR-009**: System MUST NOT send duplicate milestone notifications for the same goal and threshold — each milestone is sent at most once per goal, even if progress decreases and then re-crosses the threshold.
- **FR-010**: System MUST persist sent milestone records to survive application restarts.
- **FR-011**: System MUST include the goal title and progress percentage in milestone notification messages.
- **FR-012**: System MUST include the habit title in streak milestone notification messages. Message format: "🔥 {habitTitle}: {N}-day streak! Keep going!"
- **FR-013**: System MUST use the user's configured timezone (from profile) for all reminder scheduling. The scheduler runs in UTC, converts current time to each user's local timezone, and compares with habit.reminderTime.
- **FR-014**: System MUST fall back to UTC if the user's timezone is null or invalid.
- **FR-015**: System MUST ensure the reminder scheduler is non-overlapping — a new execution does not start while the previous one is still running.
- **FR-016**: System MUST handle Telegram API failures gracefully — log the error, skip the notification, and continue processing remaining habits. No exception propagation to the scheduler.
- **FR-017**: System MUST NOT send goal milestone notifications when the user has not connected Telegram.
- **FR-018**: System MUST NOT send goal milestone notifications for soft-deleted goals.
- **FR-019**: System MUST process GoalProgressUpdatedEvents idempotently (existing behavior preserved).
- **FR-020**: System MUST NOT send goal milestone notifications when the Telegram integration flag (`lifesync.telegram.enabled`) is disabled.

### Key Entities

- **Habit Reminder**: A scheduled notification for a specific habit at the user's configured reminder_time in their local timezone. Sent once per day for active habits with Telegram connected and the habit not yet completed today. Tracked via the `sent_reminders` table (habit_id, user_id, sent_date) with composite unique on (habit_id, sent_date).
- **Goal Milestone Notification Record**: A persistent record that tracks which milestone thresholds (25%, 50%, 75%, 100%) have been sent for each goal. Prevents duplicate notifications even across restarts.
- **Notification Message**: A Telegram message sent to a user's chat. Habit reminders: "⏰ Time to {habitTitle}! Don't break your streak!" Streak milestones: "🔥 {habitTitle}: {N}-day streak! Keep going!" Goal milestones: "🎯 {goalTitle}: {N}% complete! Keep going!" Goal 100%: "🎯 {goalTitle}: Goal achieved! Congratulations! 🎉"

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users with Telegram connected and habits with reminder_time receive reminders within 2 minutes of the specified time in their local timezone (±1 minute scheduler accuracy).
- **SC-002**: Goal milestone notifications are sent within 5 seconds of a GoalProgressUpdatedEvent being published.
- **SC-003**: Duplicate reminders for the same habit on the same day are prevented 100% of the time, including across application restarts.
- **SC-004**: Duplicate goal milestone notifications for the same goal and threshold are prevented 100% of the time, including across application restarts.
- **SC-005**: Telegram API failures do not cause reminder processing to halt — remaining habits continue to be processed.
- **SC-006**: Streak milestone notifications include the habit title in 100% of messages.
- **SC-007**: All notification features respect the Telegram integration flag — zero notifications sent when disabled.

## Assumptions

- The existing Telegram integration (TelegramNotificationSender, TelegramNotificationAdapter) is fully functional and tested.
- The existing TelegramNotificationConsumer already handles streak milestone notifications (7/14/21/30/60/90 days) — Sprint 7 enhances the message format, not the detection logic.
- The `reminder_time` field already exists on the habits table and Habit entity as a LocalTime.
- The `timezone` field already exists on UserProfile (defaults to "UTC").
- The `telegramChatId` field already exists on UserProfile and is set via `PUT /users/me/telegram`.
- The existing Kafka infrastructure, ProcessedEventRepository, and DLQ routing are reused without modification.
- The scheduled reminder job runs every minute (cron = "0 * * * * *") in UTC, converting to each user's local timezone to check for matching reminder_time. Accuracy: ±1 minute.
- Reminders are best-effort — if the scheduler misses a window (e.g., due to downtime), the reminder is not retroactively sent.
- Email and push notifications are out of scope for Sprint 7.
- Deployment is out of scope (Sprint 9).
- A new `sent_reminders` table (habit_id UUID FK, user_id UUID FK, sent_date date, composite unique on habit_id + sent_date) tracks sent habit reminders to prevent duplicates across restarts.
- A new persistence mechanism (table or similar) tracks sent goal milestone notifications (goal_id + threshold) to prevent duplicates across restarts.
- The GoalNotificationConsumer stub will be replaced with real notification logic. The TelegramNotificationConsumer will have its message template enhanced but its core logic preserved.
