# Tasks: Reminders & Notifications

**Input**: Design documents from `/specs/007-reminders-notifications/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, checklists/architecture.md

**Organization**: Tasks grouped by architectural phase (0–4) per plan.md. Each task references the user story it serves via [USx] labels. Tests included (Phase 4).

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[USx]**: Which user story this task serves (US1–US4)
- Exact file paths included in all descriptions

---

## Phase 0: DB Migrations

**Purpose**: Create `sent_reminders` and `goal_sent_milestones` tables. Regenerate jOOQ classes. Liquibase XML format, native tags, FK CASCADE, rollback blocks, no XML comments in changeSet (Constitution §V).

**⚠️ CRITICAL**: No code implementation can begin until this phase is complete and `mvn compile` succeeds.

- [ ] T001 [P] [US1] Create Liquibase migration V18 for `sent_reminders` table — columns: id (uuid PK, gen_random_uuid()), habit_id (uuid NOT NULL), user_id (uuid NOT NULL), sent_date (date NOT NULL), created_at (timestamptz NOT NULL, default now()). FK habit_id→habits(id) CASCADE, FK user_id→users(id) CASCADE. Unique constraint on (habit_id, sent_date). Indexes on habit_id, user_id. Rollback: dropAllForeignKeyConstraints + dropTable. In `lifesync-infrastructure/src/main/resources/db/changelog/notification/V18__create_sent_reminders.xml`
- [ ] T002 [P] [US2] Create Liquibase migration V19 for `goal_sent_milestones` table — columns: id (uuid PK, gen_random_uuid()), goal_id (uuid NOT NULL), threshold (int NOT NULL), sent_at (timestamptz NOT NULL, default now()), created_at (timestamptz NOT NULL, default now()). FK goal_id→goals(id) CASCADE. Unique constraint on (goal_id, threshold). Index on goal_id. Rollback: dropAllForeignKeyConstraints + dropTable. In `lifesync-infrastructure/src/main/resources/db/changelog/notification/V19__create_goal_sent_milestones.xml`
- [ ] T003 Add V18 and V19 includes to changelog master file in order after V17 in `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml`
- [ ] T004 Run `mvn generate-sources -pl lifesync-infrastructure` to regenerate jOOQ classes for sent_reminders and goal_sent_milestones tables

**Checkpoint**: `mvn compile -pl lifesync-infrastructure` passes. jOOQ classes for SENT_REMINDERS and GOAL_SENT_MILESTONES are generated. Migrations apply cleanly.

---

## Phase 1: Domain Layer — Ports

**Purpose**: Port interfaces for new repositories. Extend HabitRepository with reminder query method. Pure Java only — no Spring, jOOQ, Kafka imports (Constitution §I). No Lombok (Constitution §VII).

- [ ] T005 [P] [US1] Create SentReminderRepository port interface with methods: `boolean existsByHabitIdAndDate(HabitId habitId, LocalDate sentDate)`, `void save(HabitId habitId, UUID userId, LocalDate sentDate)` in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/notification/SentReminderRepository.java`
- [ ] T006 [P] [US2] Create GoalSentMilestoneRepository port interface with methods: `boolean existsByGoalIdAndThreshold(GoalId goalId, int threshold)`, `void save(GoalId goalId, int threshold)` in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/notification/GoalSentMilestoneRepository.java`
- [ ] T007 [P] [US1] Create HabitWithUser record in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitWithUser.java` — record with fields: `Habit habit`, `String telegramChatId`, `String timezone`. Bundles habit entity with user's Telegram and timezone data for scheduler use.
- [ ] T008 [US1] Add `List<HabitWithUser> findAllActiveWithReminderTime()` method to existing HabitRepository port interface — returns all non-deleted habits with non-null reminder_time, joined with user profile data (telegramChatId, timezone). In `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/habit/HabitRepository.java`

**Checkpoint**: `mvn compile -pl lifesync-domain` passes. No Spring/jOOQ imports in any domain file. All new types are pure Java.

---

## Phase 2: Infrastructure — Repository Implementations

**Purpose**: jOOQ implementations for new ports. Extend JooqHabitRepository with reminder query.

- [ ] T009 [P] [US1] Implement JooqSentReminderRepository (@Repository, DSLContext) — `existsByHabitIdAndDate` queries SENT_REMINDERS where habit_id = ? AND sent_date = ?. `save` inserts into SENT_REMINDERS with habit_id, user_id, sent_date, created_at. Constructor injection, all fields final. In `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/JooqSentReminderRepository.java`
- [ ] T010 [P] [US2] Implement JooqGoalSentMilestoneRepository (@Repository, DSLContext) — `existsByGoalIdAndThreshold` queries GOAL_SENT_MILESTONES where goal_id = ? AND threshold = ?. `save` inserts with goal_id, threshold, sent_at = now(), created_at = now(). Constructor injection, all fields final. In `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/JooqGoalSentMilestoneRepository.java`
- [ ] T011 [US1] Add `findAllActiveWithReminderTime()` implementation to JooqHabitRepository — SELECT habits.* plus user_profiles.telegram_chat_id and user_profiles.timezone FROM habits JOIN users ON habits.user_id = users.id JOIN user_profiles ON user_profiles.user_id = users.id WHERE habits.reminder_time IS NOT NULL AND habits.deleted_at IS NULL. Map results to List<HabitWithUser>. In `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/habit/JooqHabitRepository.java`

**Checkpoint**: `mvn compile -pl lifesync-infrastructure` passes. All three repository implementations compile.

---

## Phase 3: Scheduler & Consumer Updates

**Purpose**: HabitReminderScheduler (@Scheduled in infrastructure per CHK037). Update TelegramNotificationConsumer message format. Replace GoalNotificationConsumer stub with real milestone notification logic.

### HabitReminderScheduler (US1 + US4)

- [ ] T012 [US1] Implement HabitReminderScheduler (@Component) with @Scheduled(cron = "0 * * * * *") in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/HabitReminderScheduler.java`:
  - Constructor-inject: HabitRepository, SentReminderRepository, HabitLogRepository, TelegramNotificationSender, Clock, @Value("${lifesync.telegram.enabled}") boolean telegramEnabled
  - All fields final. Logger via LoggerFactory. No Lombok.
  - `sendReminders()` method annotated with @Scheduled:
    1. If !telegramEnabled → log DEBUG "Telegram disabled, skipping reminders" and return
    2. Load all habits via `findAllActiveWithReminderTime()` → List<HabitWithUser>
    3. For each HabitWithUser:
       a. Parse timezone via ZoneId.of(timezone) — if invalid, fallback to UTC, log WARN (CHK006/FR-014)
       b. Convert current UTC time to user's local time: `LocalTime.now(clock.withZone(zoneId))` (CHK001)
       c. Compare localTime.getHour() == reminderTime.getHour() && localTime.getMinute() == reminderTime.getMinute() (CHK008)
       d. If no match → skip
       e. Compute userLocalDate = LocalDate.now(clock.withZone(zoneId))
       f. If `sentReminderRepository.existsByHabitIdAndDate(habitId, userLocalDate)` → skip (CHK011/FR-004)
       g. If telegramChatId is null or blank → skip (FR-002)
       h. Check habit completed today: `habitLogRepository.findByHabitIdAndLogDateAndUserId(habitId, userLocalDate, userId)` → if present, skip (FR-003)
       i. Try: send Telegram message "⏰ Time to {habitTitle}! Don't break your streak!" via TelegramNotificationSender.send(chatId, message)
       j. On success: `sentReminderRepository.save(habitId, userId, userLocalDate)` — insert tracking row
       k. On Telegram failure: catch Exception, log WARN "Failed to send reminder: habitId={}, error={}", continue to next habit (CHK053/FR-016)
    4. Log INFO "Reminders sent: {count} of {total} habits processed"

### TelegramNotificationConsumer Enhancement (US3)

- [ ] T013 [US3] Update TelegramNotificationConsumer message format — change line 85 from `"You've reached a " + streak.currentStreak() + "-day streak! Keep going!"` to `"🔥 " + habit.getTitle() + ": " + streak.currentStreak() + "-day streak! Keep going!"` (CHK024/FR-012). The `habit` variable is already loaded at line 62. No other changes. In `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/notification/TelegramNotificationConsumer.java`

### GoalNotificationConsumer Real Implementation (US2)

- [ ] T014 [US2] Replace GoalNotificationConsumer stub with real milestone notification logic in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/GoalNotificationConsumer.java`:
  - Add constructor dependencies: GoalRepository, GoalSentMilestoneRepository, UserRepository, TelegramNotificationSender, @Value("${lifesync.telegram.enabled}") boolean telegramEnabled
  - Keep existing: ProcessedEventRepository, CONSUMER_GROUP = "lifesync-goal-notifier", @KafkaListener annotation, DEBUG log for topic/partition/offset
  - Keep existing: idempotency check via ProcessedEventRepository (event-level dedup)
  - New logic after idempotency check:
    1. If !telegramEnabled → mark processed, log DEBUG, return (FR-020)
    2. Load goal via `goalRepository.findByIdAndUserId(new GoalId(event.goalId()), event.userId())` — if not found or deleted → mark processed, log WARN, return (FR-018/CHK034, Constitution §III)
    3. Load user via `userRepository.findById(new UserId(event.userId()))` — if not found → mark processed, log WARN, return
    4. Get telegramChatId from user.getProfile() — if null or blank → mark processed, log DEBUG, return (FR-017)
    5. Define MILESTONES = Set.of(25, 50, 75, 100) — iterate in sorted order [25, 50, 75, 100] (CHK019)
    6. For each threshold: if `event.progressPercentage() >= threshold` (CHK029/CHK035 — uses >= not ==):
       a. If `goalSentMilestoneRepository.existsByGoalIdAndThreshold(goalId, threshold)` → skip (CHK017/FR-009)
       b. Build message: threshold == 100 ? "🎯 " + goal.getTitle() + ": Goal achieved! Congratulations! 🎉" : "🎯 " + goal.getTitle() + ": " + threshold + "% complete! Keep going!" (FR-007/CHK030)
       c. Try: `telegramNotificationSender.send(chatId, message)`
       d. On success: `goalSentMilestoneRepository.save(goalId, threshold)` (CHK021 — only save if send succeeds)
       e. On Telegram failure: catch Exception, log WARN, continue to next threshold (do NOT save milestone — CHK021)
    7. Mark event processed via `processedEventRepository.save(eventId, type, consumerGroup)`
    8. Log INFO "Goal milestone notifications processed: goalId={}, progress={}"

**Checkpoint**: `mvn compile` passes across all modules. HabitReminderScheduler compiles. TelegramNotificationConsumer message updated. GoalNotificationConsumer has real implementation. Application starts without errors.

---

## Phase 4: Tests

**Purpose**: Unit tests (MockitoExtension, @Nested per method, @DisplayName in English) + Integration tests (Testcontainers PG + Kafka, extends BaseIT). JaCoCo ≥ 80% on new code (Constitution §X).

### Unit Tests

- [ ] T015 [P] [US1] Write HabitReminderSchedulerTest — @ExtendWith(MockitoExtension.class). Mock: HabitRepository, SentReminderRepository, HabitLogRepository, TelegramNotificationSender, Clock.fixed(). @Nested per method `sendReminders`:
  1. shouldSendReminderWhenTimeMatches — habit with reminder_time matching current minute, not yet sent, not completed → verify send + save
  2. shouldSkipWhenTelegramDisabled — telegramEnabled=false → verify no send, no repository calls
  3. shouldSkipWhenAlreadySentToday — existsByHabitIdAndDate returns true → verify no send
  4. shouldSkipWhenHabitCompletedToday — findByHabitIdAndLogDateAndUserId returns present → verify no send
  5. shouldSkipWhenNoTelegramChatId — chatId is null → verify no send
  6. shouldSkipWhenTimeDoesNotMatch — reminder_time "08:00" but current local time is "09:00" → verify no send
  7. shouldHandleInvalidTimezoneWithUtcFallback — invalid timezone string → verify falls back to UTC, logs WARN
  8. shouldContinueOnTelegramFailure — send throws RuntimeException → verify no save for failed habit, continue to next habit
  In `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/notification/HabitReminderSchedulerTest.java`

- [ ] T016 [P] [US2] Write GoalNotificationConsumerTest — @ExtendWith(MockitoExtension.class). Mock: GoalRepository, GoalSentMilestoneRepository, UserRepository, TelegramNotificationSender, ProcessedEventRepository. @Nested per method `consume`:
  1. shouldSendMilestoneAt25Percent — progress=25, no prior milestones → verify send "🎯 ... 25% complete...", verify save(goalId, 25)
  2. shouldSendMilestoneAt100PercentWithSpecialMessage — progress=100 → verify "🎯 ... Goal achieved! Congratulations! 🎉"
  3. shouldSendMultipleMilestonesOnJump — progress=80, no prior milestones → verify 25%, 50%, 75% all sent and saved (3 sends, 3 saves)
  4. shouldSkipAlreadySentMilestone — progress=50, existsByGoalIdAndThreshold(goalId, 25) returns true → verify only 50% sent, not 25%
  5. shouldSkipWhenTelegramDisabled — telegramEnabled=false → verify no send, event marked processed
  6. shouldSkipWhenNoTelegramChatId — chatId null → verify no send, event marked processed
  7. shouldSkipDeletedGoal — findById returns goal with deletedAt set → verify no send
  8. shouldNotSaveMilestoneOnSendFailure — send throws for 50% → verify 50% milestone NOT saved, 25% IS saved (CHK021)
  9. shouldHandleDuplicateEvent — existsByEventIdAndConsumerGroup returns true → verify no processing
  In `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/goal/GoalNotificationConsumerTest.java`

- [ ] T017 [P] [US3] Update existing TelegramNotificationConsumerTest — modify `shouldSendNotificationForMilestone` test (line 108) to assert new message format `"🔥 " + habitTitle + ": " + milestone + "-day streak! Keep going!"` instead of old format. Add new test `shouldIncludeHabitTitleInMessage` to verify habit title is present in the notification message. In `lifesync-infrastructure/src/test/java/ru/zahaand/lifesync/infrastructure/notification/TelegramNotificationConsumerTest.java`

### Integration Tests

- [ ] T018 [US1] Write HabitReminderSchedulerIT (extends BaseIT) — end-to-end: register user with timezone + telegram, create habit with reminder_time matching current test clock minute, invoke scheduler, verify sent_reminders row created. Test duplicate prevention: invoke scheduler again, verify no second row. Test completed habit skip: complete habit then invoke scheduler, verify no reminder sent. In `lifesync-web/src/test/java/ru/zahaand/lifesync/web/notification/HabitReminderSchedulerIT.java`

- [ ] T019 [US2] Write GoalNotificationConsumerIT (extends BaseIT) — end-to-end: register user with telegram, create goal, publish GoalProgressUpdatedEvent with progress=25 → verify goal_sent_milestones row for 25%. Publish another event with progress=75 → verify 50% and 75% rows added. Publish duplicate event → verify idempotency. Publish event with progress=100 → verify 100% row. In `lifesync-web/src/test/java/ru/zahaand/lifesync/web/notification/GoalNotificationConsumerIT.java`

- [ ] T020 [US3] Write TelegramNotificationConsumerIT (extends BaseIT) — publish HabitCompletedEvent for a habit at 7-day streak → verify notification message contains habit title. Use Awaitility for async assertion. In `lifesync-web/src/test/java/ru/zahaand/lifesync/web/notification/TelegramNotificationConsumerIT.java`

**Checkpoint**: `mvn verify` passes. All tests green with Testcontainers. JaCoCo ≥ 80% on new code.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 0 (Migrations)**: No dependencies — start immediately. BLOCKS all subsequent phases.
- **Phase 1 (Domain)**: Depends on Phase 0 (needs generated jOOQ types for reference). BLOCKS Phases 2–3.
- **Phase 2 (Infrastructure — Repos)**: Depends on Phase 1 (port interfaces). BLOCKS Phase 3.
- **Phase 3 (Scheduler + Consumers)**: Depends on Phase 2 (repository implementations).
- **Phase 4 (Tests)**: Unit tests can start after Phase 3. ITs need full application context.

### User Story Dependencies

- **US1 (Habit Reminders)**: Foundation — requires Phases 0–2 complete, then T012
- **US2 (Goal Milestones)**: Independent from US1 — requires Phases 0–2 complete, then T014
- **US3 (Streak Message Enhancement)**: Independent — T013 can run any time after Phase 2
- **US4 (Timezone)**: Integrated into US1 (T012 handles timezone conversion)

### Parallel Opportunities per Phase

**Phase 0**: T001–T002 [P] (different migration files), T003 after both, T004 last
**Phase 1**: T005–T007 [P] (different files). T008 depends on T007 (uses HabitWithUser)
**Phase 2**: T009–T010 [P] (different repo files). T011 depends on T007/T008
**Phase 3**: T013 [P] with T012 and T014 (different files). T012 and T014 are independent
**Phase 4**: T015–T017 [P] (unit tests). T018–T020 sequential (shared DB state)

---

## Implementation Strategy

### MVP First (US1 — Habit Reminders)

1. Phase 0: V18 migration + jOOQ regen
2. Phase 1: SentReminderRepository port, HabitWithUser, HabitRepository extension
3. Phase 2: JooqSentReminderRepository, JooqHabitRepository extension
4. Phase 3: HabitReminderScheduler (T012)
5. **STOP and VALIDATE**: Create habit with reminder_time, verify Telegram message sent

### Incremental Delivery

1. US1 (Habit Reminders) → **independently testable MVP**
2. US2 (Goal Milestones) → adds motivational feedback on goal progress
3. US3 (Streak Message Enhancement) → improves existing notification quality
4. US4 (Timezone) → integrated into US1 (not separate)
5. Phase 4 (Tests) → quality gate

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in same phase
- [USx] label maps task to specific user story for traceability
- CHK references map to `checklists/architecture.md` items
- Constitution references ensure compliance with project principles
- Commit granularity: one commit per phase (Constitution §Dev Standards 7)
- No new API endpoints — this sprint is backend-only
- KafkaGoalEventPublisher and existing Kafka infrastructure are reused unchanged
- TelegramNotificationAdapter is reused unchanged — only consumer message formats change
