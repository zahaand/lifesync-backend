# Data Model: Reminders & Notifications

**Branch**: `007-reminders-notifications` | **Date**: 2026-03-31

## New Tables

### sent_reminders (V18)

Tracks which habit reminders have been sent on which day to prevent duplicates.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, default gen_random_uuid() | Primary key |
| habit_id | UUID | NOT NULL, FK habits(id) CASCADE | The habit this reminder was sent for |
| user_id | UUID | NOT NULL, FK users(id) CASCADE | The user who received the reminder |
| sent_date | DATE | NOT NULL | The calendar date (user's local date) the reminder was sent |
| created_at | TIMESTAMPTZ | NOT NULL, default now() | When the record was created |

**Unique constraint**: `(habit_id, sent_date)` — prevents duplicate reminders for same habit on same day
**Indexes**: `habit_id`, `user_id`, `(habit_id, sent_date)` covered by unique constraint

### goal_sent_milestones (V19)

Tracks which progress milestones have been notified for each goal to prevent duplicates.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, default gen_random_uuid() | Primary key |
| goal_id | UUID | NOT NULL, FK goals(id) CASCADE | The goal this milestone was sent for |
| threshold | INT | NOT NULL | The milestone percentage (25, 50, 75, or 100) |
| sent_at | TIMESTAMPTZ | NOT NULL, default now() | When the notification was sent |
| created_at | TIMESTAMPTZ | NOT NULL, default now() | When the record was created |

**Unique constraint**: `(goal_id, threshold)` — prevents duplicate milestone notifications
**Indexes**: `goal_id`, `(goal_id, threshold)` covered by unique constraint

## Existing Tables (no changes)

### habits (referenced)

Relevant fields for reminders:
- `id` UUID PK
- `user_id` UUID FK
- `title` VARCHAR — used in reminder message
- `reminder_time` TIME — HH:mm, nullable (V16 migration)
- `deleted_at` TIMESTAMPTZ — soft delete filter

### user_profiles (referenced)

Relevant fields:
- `user_id` UUID FK
- `timezone` VARCHAR — defaults to "UTC", used for reminder time conversion
- `telegram_chat_id` VARCHAR — nullable, must be set for notifications

### goals (referenced)

Relevant fields:
- `id` UUID PK
- `user_id` UUID FK
- `title` VARCHAR — used in milestone notification message
- `deleted_at` TIMESTAMPTZ — soft delete filter

## Port Interfaces (Domain)

### SentReminderRepository

```
Package: ru.zahaand.lifesync.domain.notification

boolean existsByHabitIdAndDate(HabitId habitId, LocalDate sentDate)
void save(HabitId habitId, UUID userId, LocalDate sentDate)
```

### GoalSentMilestoneRepository

```
Package: ru.zahaand.lifesync.domain.notification

boolean existsByGoalIdAndThreshold(GoalId goalId, int threshold)
void save(GoalId goalId, int threshold)
```

### HabitRepository (extended — existing port)

```
Package: ru.zahaand.lifesync.domain.habit

// NEW method
List<HabitWithUser> findAllActiveWithReminderTime()
```

`HabitWithUser` is a record that bundles Habit entity with user's telegramChatId and timezone. This avoids N+1 queries — a single SQL join retrieves all needed data.

```
record HabitWithUser(Habit habit, String telegramChatId, String timezone)
```

## Data Flow Diagrams

### Habit Reminder Flow

```
@Scheduled (every minute)
    │
    ├── telegramEnabled? NO → return
    │
    ├── findAllActiveWithReminderTime() → List<HabitWithUser>
    │
    └── For each HabitWithUser:
            ├── Convert UTC now → user's local time (timezone)
            ├── localTime.getHour() == reminderTime.getHour()
            │   && localTime.getMinute() == reminderTime.getMinute()?
            │       NO → skip
            │
            ├── existsByHabitIdAndDate(habitId, localDate)?
            │       YES → skip (already sent today)
            │
            ├── habitCompletedToday? (check habit_logs for today's date)
            │       YES → skip (already completed)
            │
            ├── telegramChatId null/blank?
            │       YES → skip
            │
            ├── TelegramNotificationSender.send(chatId, message)
            │       FAIL → log WARN, continue
            │
            └── SentReminderRepository.save(habitId, userId, localDate)
```

### Goal Milestone Notification Flow

```
GoalNotificationConsumer receives GoalProgressUpdatedEvent
    │
    ├── Idempotency check (ProcessedEventRepository) → duplicate? skip
    │
    ├── telegramEnabled? NO → mark processed, return
    │
    ├── Load goal via GoalRepository.findByIdAndUserId(goalId, userId)
    │       NOT FOUND or DELETED → mark processed, return
    │
    ├── Load user via UserRepository.findById(userId)
    │       telegramChatId null? → mark processed, return
    │
    ├── For each threshold in [25, 50, 75, 100]:
    │       ├── progressPercentage >= threshold?
    │       │       NO → skip this threshold
    │       │
    │       ├── existsByGoalIdAndThreshold(goalId, threshold)?
    │       │       YES → skip (already sent)
    │       │
    │       ├── Build message:
    │       │       100% → "🎯 {title}: Goal achieved! Congratulations! 🎉"
    │       │       else → "🎯 {title}: {N}% complete! Keep going!"
    │       │
    │       ├── TelegramNotificationSender.send(chatId, message)
    │       │       FAIL → log WARN, continue
    │       │
    │       └── GoalSentMilestoneRepository.save(goalId, threshold)
    │
    └── ProcessedEventRepository.save(eventId, type, consumerGroup)
```

### Streak Milestone Message Enhancement Flow

```
TelegramNotificationConsumer receives HabitCompletedEvent
    │
    ├── (existing logic unchanged — load habit, calculate streak, check milestone)
    │
    └── If milestone reached:
            ├── Build message: "🔥 {habit.getTitle()}: {streak}-day streak! Keep going!"
            │   (was: "You've reached a {streak}-day streak! Keep going!")
            │
            └── TelegramNotificationSender.send(chatId, message)
```
