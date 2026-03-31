# Tasks: Goals Feature

**Input**: Design documents from `/specs/006-goals-feature/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, checklists/architecture.md

**Organization**: Tasks grouped by architectural phase (0–6) per plan.md. Each task references the user story it serves via [USx] labels. Tests included (Phase 6) as requested.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[USx]**: Which user story this task serves (US1–US6)
- Exact file paths included in all descriptions

---

## Phase 0: OpenAPI Spec Update

**Purpose**: Add Goals tag, 12 endpoints, 11 schemas to existing lifesync-api.yaml. Full Principle XII documentation on every endpoint and schema field. YAML before code (Constitution §II).

**⚠️ CRITICAL**: No code implementation can begin until this phase is complete and `mvn generate-sources` succeeds.

- [x] T001 Add Goals tag and goal CRUD paths (`POST /api/v1/goals`, `GET /api/v1/goals`, `GET /api/v1/goals/{goalId}`, `PATCH /api/v1/goals/{goalId}`, `DELETE /api/v1/goals/{goalId}`) with full Principle XII documentation (summary, description with business rules + how-to-test, named examples, error explanations) in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [x] T002 Add goal progress endpoint (`PATCH /api/v1/goals/{goalId}/progress`) with Principle XII documentation — clearly distinguish from `PATCH /api/v1/goals/{goalId}` per CHK043 in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [x] T003 Add milestone endpoints (`POST /api/v1/goals/{goalId}/milestones`, `PATCH /api/v1/goals/{goalId}/milestones/{milestoneId}`, `DELETE /api/v1/goals/{goalId}/milestones/{milestoneId}`) with Principle XII documentation in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [x] T004 Add habit-goal link endpoints (`POST /api/v1/goals/{goalId}/habits`, `GET /api/v1/goals/{goalId}/habits`, `DELETE /api/v1/goals/{goalId}/habits/{habitId}`) with Principle XII documentation in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [x] T005 Add all 11 request/response schemas (GoalCreateRequestDto, GoalUpdateRequestDto, GoalProgressUpdateRequestDto, GoalResponseDto, GoalDetailResponseDto, GoalPageResponseDto, MilestoneCreateRequestDto, MilestoneUpdateRequestDto, MilestoneResponseDto, GoalHabitLinkRequestDto, GoalHabitLinkResponseDto) with `description` on every field per CHK040 in `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [x] T006 Run `mvn generate-sources -pl lifesync-api-spec` and verify GoalApi interface and all DTOs are generated successfully

**Checkpoint**: `mvn generate-sources` passes. GoalApi interface exists with all 12 endpoint methods. All 11 DTOs generated. Pagination format (GoalPageResponseDto) matches existing HabitPageResponseDto pattern per CHK042.

---

## Phase 1: Domain Layer

**Purpose**: Pure Java domain entities, value objects, port interfaces, and exceptions. No Spring, jOOQ, Kafka, or Jackson imports (Constitution §I). No Lombok (Constitution §VII). Constructor injection, all fields final (Constitution §IX).

### Value Objects & Enums

- [x] T007 [P] Create GoalId record value object with UUID validation in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalId.java`
- [x] T008 [P] Create GoalMilestoneId record value object with UUID validation in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalMilestoneId.java`
- [x] T009 [P] Create GoalHabitLinkId record value object with UUID validation in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalHabitLinkId.java`
- [x] T010 [P] Create GoalStatus enum (ACTIVE, COMPLETED) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalStatus.java`

### Entities

- [x] T011 [P] [US1] Create Goal entity (final class, immutable copy-on-write) with fields: GoalId, UUID userId, String title, String description (nullable), LocalDate targetDate (nullable), GoalStatus status, int progress (0-100), Instant createdAt/updatedAt/deletedAt. Methods: update(), updateProgress() (sets COMPLETED if 100 per CHK006), softDelete(), isDeleted(), isActive(). Validate progress range 0-100 per CHK053 in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/Goal.java`
- [x] T012 [P] [US2] Create GoalMilestone entity (final class, immutable copy-on-write) with fields: GoalMilestoneId, GoalId, String title, int sortOrder, boolean completed, Instant completedAt (nullable), Instant createdAt/updatedAt/deletedAt. Methods: complete(), uncomplete(), update(), softDelete(), isDeleted() in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalMilestone.java`
- [x] T013 [P] [US3] Create GoalHabitLink entity (final class) with fields: GoalHabitLinkId, GoalId, HabitId (from habit domain), Instant createdAt/updatedAt in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalHabitLink.java`

### Port Interfaces

- [x] T014 [P] [US1] Create GoalRepository port interface with methods: save(Goal), findByIdAndUserId(GoalId, UUID), findAllByUserId(UUID userId, GoalStatus status, int page, int size) → GoalPage (status is nullable — null returns all non-deleted goals), update(Goal). Include GoalPage record. All queries must support userId predicate per CHK033 in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalRepository.java`
- [x] T015 [P] [US2] Create GoalMilestoneRepository port interface with methods: save(GoalMilestone), findByIdAndGoalId(GoalMilestoneId, GoalId), findAllActiveByGoalId(GoalId), update(GoalMilestone) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalMilestoneRepository.java`
- [x] T016 [P] [US3] Create GoalHabitLinkRepository port interface with methods: save(GoalHabitLink), existsByGoalIdAndHabitId(GoalId, HabitId), findAllByGoalId(GoalId), findActiveGoalIdsByHabitId(HabitId) → List<GoalId>, deleteByGoalIdAndHabitId(GoalId, HabitId), countTotalByGoalId(GoalId), countCompletedDaysByGoalId(GoalId) (distinct dates with at least one linked habit completed — joins goal_habits + habit_logs), countExpectedCompletionsByGoalId(GoalId, LocalDate createdAt, LocalDate endDate) (counts expected habit performances in [createdAt, endDate] based on frequency: DAILY = all days, WEEKLY = number of weeks Monday-based, CUSTOM = days matching targetDaysOfWeek; endDate = min(today, targetDate) if targetDate exists, else today) per CHK035 in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/GoalHabitLinkRepository.java`

### Domain Exceptions

- [x] T017 [P] Create GoalNotFoundException (extends RuntimeException, single String message constructor) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/exception/GoalNotFoundException.java`
- [x] T018 [P] Create GoalHabitLinkNotFoundException (extends RuntimeException) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/exception/GoalHabitLinkNotFoundException.java`
- [x] T019 [P] Create DuplicateGoalHabitLinkException (extends RuntimeException) in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/goal/exception/DuplicateGoalHabitLinkException.java`

**Checkpoint**: `mvn compile -pl lifesync-domain` passes. No Spring/jOOQ/Kafka imports in any domain file (Constitution §I). All fields final. All public methods return non-null or Optional.

---

## Phase 2: Application Layer — Use Cases

**Purpose**: 12 use cases as plain Java classes. No @Service annotation — wired via UseCaseConfig @Bean methods (CHK050). @Transactional on write operations only. Event publishing via ApplicationEventPublisher inside try/catch (fire-and-forget per CHK025). No jOOQ/Kafka imports (Constitution §I).

### Goal CRUD Use Cases (US1)

- [x] T020 [P] [US1] Create CreateGoalUseCase (GoalRepository, Clock) with @Transactional execute(UUID userId, String title, String description, LocalDate targetDate) → Goal. Progress defaults to 0, status ACTIVE per CHK001 in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/CreateGoalUseCase.java`
- [x] T021 [P] [US1] Create GetGoalUseCase (GoalRepository, GoalMilestoneRepository, GoalHabitLinkRepository) — returns goal with milestones + linked habit IDs. Validates ownership via findByIdAndUserId, throws GoalNotFoundException if not found in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/GetGoalUseCase.java`
- [x] T022 [P] [US1] Create GetGoalsUseCase (GoalRepository) — paginated list via findAllByUserId(userId, status, page, size) with optional GoalStatus filter (nullable), userId isolation in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/GetGoalsUseCase.java`
- [x] T023 [P] [US1] Create UpdateGoalUseCase (GoalRepository, Clock) with @Transactional and UpdateCommand record (title, description, descriptionProvided, targetDate, targetDateProvided, status). Validates ownership in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/UpdateGoalUseCase.java`
- [x] T024 [P] [US1] Create DeleteGoalUseCase (GoalRepository, Clock) — soft delete via goal.softDelete(). Validates ownership in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/DeleteGoalUseCase.java`

### Milestone Use Cases (US2)

- [x] T025 [P] [US2] Create AddMilestoneUseCase (GoalRepository, GoalMilestoneRepository, Clock) with @Transactional. Validates goal ownership first (CHK036), then creates milestone in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/AddMilestoneUseCase.java`
- [x] T026 [P] [US2] Create UpdateMilestoneUseCase (GoalRepository, GoalMilestoneRepository, Clock) with @Transactional. Handles complete/uncomplete (sets/clears completedAt), title, sortOrder via UpdateCommand pattern. Validates goal ownership first in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/UpdateMilestoneUseCase.java`
- [x] T027 [P] [US2] Create DeleteMilestoneUseCase (GoalRepository, GoalMilestoneRepository, Clock) — soft delete milestone. Validates goal ownership first in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/DeleteMilestoneUseCase.java`

### Habit-Goal Linking Use Cases (US3)

- [x] T028 [P] [US3] Create LinkHabitToGoalUseCase (GoalRepository, GoalHabitLinkRepository, HabitRepository, RecalculateGoalProgressUseCase, Clock) with @Transactional. Validates BOTH goal and habit ownership (CHK010). Checks duplicate via existsByGoalIdAndHabitId → throws DuplicateGoalHabitLinkException. After linking, triggers immediate progress recalculation (FR-014a, CHK010) in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/LinkHabitToGoalUseCase.java`
- [x] T029 [P] [US3] Create UnlinkHabitFromGoalUseCase (GoalRepository, GoalHabitLinkRepository, RecalculateGoalProgressUseCase, Clock) with @Transactional. Validates goal ownership. Deletes link. After unlinking, immediately recalculates goal progress using RecalculateGoalProgressUseCase. If no habits remain linked (countTotalByGoalId == 0), recalculation is skipped — progress stays as-is (manual-only mode per spec edge case). CHK014: when a habit is soft-deleted, its goal links remain but progress is recalculated via GoalProgressConsumer (HabitCompletedEvent published on deletion) in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/UnlinkHabitFromGoalUseCase.java`

### Progress Use Cases (US4, US5)

- [x] T030 [US4] Create UpdateGoalProgressUseCase (GoalRepository, ApplicationEventPublisher, Clock) with @Transactional. Manual PATCH path — validates progress 0-100, validates ownership, calls goal.updateProgress() (auto-COMPLETED at 100 per CHK006), publishes GoalProgressUpdatedEvent with habitId=null for manual path (CHK009) inside try/catch in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/UpdateGoalProgressUseCase.java`
- [x] T031 [US5] Create RecalculateGoalProgressUseCase (GoalRepository, GoalHabitLinkRepository, ApplicationEventPublisher, Clock) with @Transactional. Called by consumer AND by LinkHabitToGoalUseCase AND by UnlinkHabitFromGoalUseCase. For a given goalId: count totalLinked = countTotalByGoalId, if 0 → skip (CHK001/CHK005 — manual-only mode). Compute endDate = min(today, goal.targetDate) if targetDate exists, else today. Count completedDays = countCompletedDaysByGoalId(goalId), expectedCompletions = countExpectedCompletionsByGoalId(goalId, goal.createdAt, endDate). If expectedCompletions == 0 but totalLinked > 0 (habits exist but none expected in period yet — e.g. goal created today), set progress = 0 and skip event publishing. Otherwise calculate progress = Math.min(Math.round((float) completedDays / expectedCompletions * 100), 100) (CHK007), call goal.updateProgress(), goalRepository.update(), publish GoalProgressUpdatedEvent per goal (CHK017/CHK018) inside try/catch in `lifesync-application/src/main/java/ru/zahaand/lifesync/application/goal/RecalculateGoalProgressUseCase.java`

**Checkpoint**: `mvn compile -pl lifesync-application` passes. No jOOQ/Kafka/Spring MVC imports. Only Spring @Transactional and ApplicationEventPublisher permitted. All use cases are plain Java classes (no @Service/@Component).

---

## Phase 3: Infrastructure — Repositories

**Purpose**: jOOQ repository implementations. All reads include DELETED_AT.isNull() and userId predicates (CHK033). Follow JooqHabitRepository patterns exactly.

- [x] T032 [P] [US1] Implement JooqGoalRepository (@Repository, DSLContext) — save(), findByIdAndUserId() with deleted_at IS NULL + userId predicate, findAllByUserId(userId, status, page, size) with optional GoalStatus filter (nullable) + deleted_at IS NULL + pagination (selectCount + offset/limit), update(). Map OffsetDateTime↔Instant. Handle nullable description/targetDate/deletedAt in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/JooqGoalRepository.java`
- [x] T033 [P] [US2] Implement JooqGoalMilestoneRepository (@Repository, DSLContext) — save(), findByIdAndGoalId() with deleted_at IS NULL, findAllActiveByGoalId() ordered by sort_order ASC with deleted_at IS NULL, update(). Goal ownership validation is done at use case level (CHK034) in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/JooqGoalMilestoneRepository.java`
- [x] T034 [US3] Implement JooqGoalHabitLinkRepository (@Repository, DSLContext) — save(), existsByGoalIdAndHabitId(), findAllByGoalId(), findActiveGoalIdsByHabitId() (joins goals to filter deleted_at IS NULL per CHK020/CHK035), deleteByGoalIdAndHabitId(), countTotalByGoalId(), countCompletedDaysByGoalId() (SELECT COUNT(DISTINCT hl.log_date) from goal_habits gh JOIN habit_logs hl ON gh.habit_id = hl.habit_id WHERE gh.goal_id = ? AND hl.deleted_at IS NULL per CHK002), countExpectedCompletionsByGoalId(GoalId, LocalDate createdAt, LocalDate endDate) (joins goal_habits with habits, sums expected days per habit in [createdAt, endDate] based on frequency: DAILY = all days, WEEKLY = number of Monday-based weeks, CUSTOM = days matching target_days_of_week jsonb) in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/JooqGoalHabitLinkRepository.java`

**Checkpoint**: `mvn compile -pl lifesync-infrastructure` passes. All repository reads include soft-delete filters. userId predicates on all goal queries. countCompletedDaysByGoalId correctly joins goal_habits with habit_logs. countExpectedCompletionsByGoalId joins goal_habits with habits for frequency-aware calculation.

---

## Phase 4: Infrastructure — Kafka Consumers

**Purpose**: 3 Kafka consumers. All use ProcessedEventRepository for idempotency (eventId + consumerGroup per CHK017). Follow StreakCalculatorConsumer pattern exactly.

- [ ] T035 [US5] Implement GoalProgressConsumer (@Component) — @KafkaListener(topics = "habit.log.completed", groupId = "lifesync-goal-progress"). Receives ConsumerRecord<String, HabitCompletedEvent>. Idempotency check first. Calls RecalculateGoalProgressUseCase for ALL active goals linked to event.habitId() (CHK017/CHK018). Handles both habit completion AND log deletion identically (CHK003/CHK008). Saves processed event AFTER all goals recalculated (CHK023). CHK038: consumer uses event.userId() — goal ownership is implicitly guaranteed because findActiveGoalIdsByHabitId returns only goals whose owner matches the habit owner (FK constraint). DEBUG log topic/partition/offset, INFO on success, WARN on duplicate. Note: GoalProgressUpdatedEvent.habitId is nullable (null for manual progress updates per CHK009). Kafka JsonSerializer handles null fields correctly — no special handling needed. Consumers must not rely on habitId being present in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/GoalProgressConsumer.java`
- [ ] T036 [P] [US6] Implement GoalAnalyticsConsumer (@Component) stub — @KafkaListener(topics = "goal.progress.updated", groupId = "lifesync-goal-analytics"). Idempotency check. Logs goal ID + progress at INFO. Marks event processed in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/GoalAnalyticsConsumer.java`
- [ ] T037 [P] [US6] Implement GoalNotificationConsumer (@Component) stub — @KafkaListener(topics = "goal.progress.updated", groupId = "lifesync-goal-notification"). Idempotency check. Logs notification placeholder at INFO. Marks event processed. Real thresholds (25/50/75/100%) deferred to Sprint 7 (CHK030) in `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/goal/GoalNotificationConsumer.java`

**Checkpoint**: `mvn compile -pl lifesync-infrastructure` passes. All 3 consumers compile. GoalProgressConsumer delegates to RecalculateGoalProgressUseCase. Both stubs use ProcessedEventRepository for idempotency. DLQ routing handled by existing KafkaConsumerConfig (CHK022/CHK032). CHK021: if recalculation fails for one goal in the multi-goal loop, the entire event is retried (no partial commit) — @Transactional on the use case ensures atomicity per goal, but consumer-level retry replays the full event.

---

## Phase 5: Web Layer & Config

**Purpose**: GoalController implementing generated GoalApi interface. Bean wiring in UseCaseConfig. Exception handler updates. Follow HabitController patterns exactly.

- [ ] T038 [US1] Implement GoalController (@RestController, implements GoalApi) — all 12 endpoint methods. Constructor-inject all use cases. getCurrentUserId() via SecurityContextHolder pattern. Inline DTO↔domain conversion. POST → 201, GET → 200, DELETE → 204. Pagination response assembly per existing pattern (CHK042). CHK038: all nested resource operations (milestones, habit links) validate parent goal ownership at use case level before proceeding in `lifesync-web/src/main/java/ru/zahaand/lifesync/web/goal/GoalController.java`
- [ ] T039 Add GoalNotFoundException → 404, GoalHabitLinkNotFoundException → 404, DuplicateGoalHabitLinkException → 409 handlers (CHK047) following existing buildResponse pattern in `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/GlobalExceptionHandler.java`
- [ ] T040 Add @Bean methods for all 12 goal use cases (manual new, no component scanning per CHK050): CreateGoalUseCase, GetGoalUseCase, GetGoalsUseCase, UpdateGoalUseCase, DeleteGoalUseCase, AddMilestoneUseCase, UpdateMilestoneUseCase, DeleteMilestoneUseCase, LinkHabitToGoalUseCase, UnlinkHabitFromGoalUseCase, UpdateGoalProgressUseCase, RecalculateGoalProgressUseCase in `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/UseCaseConfig.java`

**Checkpoint**: `mvn compile` passes across all modules. GoalController compiles against generated GoalApi. All 12 use cases wired as beans. Exception handlers registered. Full application starts without errors.

---

## Phase 6: Tests

**Purpose**: Unit tests (MockitoExtension, @Nested per method, @DisplayName in English) + Integration tests (Testcontainers PG + Kafka, extends BaseIT). JaCoCo ≥ 80% on domain + application (Constitution §X).

### Unit Tests — Domain

- [ ] T041 [P] [US1] Write GoalTest — @Nested per method: update(), updateProgress() (test 0→50, 50→100 triggers COMPLETED, boundary values 0 and 100 per CHK006), softDelete(), isDeleted(), isActive(). @ParameterizedTest for progress validation (0-100 range per CHK053) in `lifesync-domain/src/test/java/ru/zahaand/lifesync/domain/goal/GoalTest.java`
- [ ] T042 [P] [US2] Write GoalMilestoneTest — @Nested: complete(), uncomplete(), update(), softDelete(), isDeleted() in `lifesync-domain/src/test/java/ru/zahaand/lifesync/domain/goal/GoalMilestoneTest.java`

### Unit Tests — Application Use Cases

- [ ] T043 [P] [US1] Write CreateGoalUseCaseTest — @ExtendWith(MockitoExtension.class). Test: success path (ACTIVE status, progress 0), null title rejection in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/CreateGoalUseCaseTest.java`
- [ ] T044 [P] [US1] Write GetGoalUseCaseTest — success with milestones + linked habit IDs, GoalNotFoundException when not found, ownership isolation in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/GetGoalUseCaseTest.java`
- [ ] T045 [P] [US1] Write GetGoalsUseCaseTest — paginated list, status filter, userId isolation in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/GetGoalsUseCaseTest.java`
- [ ] T046 [P] [US1] Write UpdateGoalUseCaseTest — partial update, ownership check, GoalNotFoundException in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/UpdateGoalUseCaseTest.java`
- [ ] T047 [P] [US1] Write DeleteGoalUseCaseTest — soft delete, ownership check in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/DeleteGoalUseCaseTest.java`
- [ ] T048 [P] [US2] Write AddMilestoneUseCaseTest — success, goal ownership check (CHK036), GoalNotFoundException in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/AddMilestoneUseCaseTest.java`
- [ ] T049 [P] [US2] Write UpdateMilestoneUseCaseTest — complete/uncomplete, title/sortOrder update, goal ownership check in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/UpdateMilestoneUseCaseTest.java`
- [ ] T050 [P] [US2] Write DeleteMilestoneUseCaseTest — soft delete, goal ownership check in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/DeleteMilestoneUseCaseTest.java`
- [ ] T051 [P] [US3] Write LinkHabitToGoalUseCaseTest — success with immediate recalculation (FR-014a), duplicate rejection (DuplicateGoalHabitLinkException), both-sides ownership validation (CHK010), habit not found in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/LinkHabitToGoalUseCaseTest.java`
- [ ] T052 [P] [US3] Write UnlinkHabitFromGoalUseCaseTest — success (progress retained), goal ownership check, link not found in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/UnlinkHabitFromGoalUseCaseTest.java`
- [ ] T053 [P] [US4] Write UpdateGoalProgressUseCaseTest — success (publishes event with habitId=null per CHK009), progress 100 → COMPLETED, validation 0-100, ownership check, event publishing failure is non-fatal (CHK025) in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/UpdateGoalProgressUseCaseTest.java`
- [ ] T054 [P] [US5] Write RecalculateGoalProgressUseCaseTest — success with correct formula (CHK001), skip when no habits linked (count=0 per CHK005), multi-goal recalculation, progress 100 → COMPLETED, event published per goal (CHK027) in `lifesync-application/src/test/java/ru/zahaand/lifesync/application/goal/RecalculateGoalProgressUseCaseTest.java`

### Integration Tests

- [ ] T055 [US1] Write GoalControllerIT (extends BaseIT) — full CRUD: create (201), get single (200 with milestones/linkedHabitIds), get list with pagination + status filter, update (200), delete (204 + excluded from list), validation errors (400), ownership isolation (404 for other user's goal per CHK037) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalControllerIT.java`
- [ ] T056 [US2] Write GoalMilestoneControllerIT (extends BaseIT) — add milestone (201), update/complete/uncomplete (200), delete (204), validation (400), goal ownership check (404) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalMilestoneControllerIT.java`
- [ ] T057 [US3] Write GoalHabitLinkControllerIT (extends BaseIT) — link habit (201), list linked habits (200), unlink (204), duplicate rejection (409), cross-user ownership (404 for other user's habit per CHK010) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalHabitLinkControllerIT.java`
- [ ] T058 [US4] Write GoalProgressIT (extends BaseIT) — manual progress update (200), progress 100 → COMPLETED status transition, validation out-of-range (400), ownership isolation (404) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalProgressIT.java`
- [ ] T059 [US5] Write GoalProgressConsumerIT (extends BaseIT) — end-to-end: complete habit → GoalProgressConsumer recalculates → GoalProgressUpdatedEvent published. Test multi-goal recalculation (CHK018). Test idempotency (duplicate event skipped per CHK017). Test habit log deletion → progress recalculated downward (CHK003/CHK008). Test soft-deleted goal skipped (CHK020) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalProgressConsumerIT.java`
- [ ] T060 [US6] Write GoalConsumerIT (extends BaseIT) — GoalAnalyticsConsumer + GoalNotificationConsumer: event processing, idempotency (duplicate WARN), DLQ routing after retry exhaustion (CHK022) in `lifesync-web/src/test/java/ru/zahaand/lifesync/web/goal/GoalConsumerIT.java`

**Checkpoint**: `mvn verify` passes. JaCoCo ≥ 80% on lifesync-domain + lifesync-application. All ITs green with Testcontainers.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 0 (OpenAPI)**: No dependencies — start immediately. BLOCKS all subsequent phases.
- **Phase 1 (Domain)**: Depends on Phase 0 (needs generated types for reference). BLOCKS Phases 2-5.
- **Phase 2 (Application)**: Depends on Phase 1 (port interfaces). BLOCKS Phases 3-5.
- **Phase 3 (Infrastructure — Repos)**: Depends on Phase 2 (use case contracts). BLOCKS Phases 4-5.
- **Phase 4 (Infrastructure — Consumers)**: Depends on Phase 3 (repositories for consumer logic).
- **Phase 5 (Web & Config)**: Depends on Phases 2-4 (all use cases + consumers must exist for wiring).
- **Phase 6 (Tests)**: Depends on all previous phases. Unit tests can start after Phase 2; ITs after Phase 5.

### User Story Dependencies

- **US1 (Goal Management)**: Foundation — no dependencies on other stories
- **US2 (Milestones)**: Depends on US1 (goals must exist to add milestones)
- **US3 (Habit-Goal Linking)**: Depends on US1 + existing habits feature
- **US4 (Manual Progress)**: Depends on US1 (goals must exist)
- **US5 (Auto Progress Events)**: Depends on US3 (links must exist) + US4 (RecalculateGoalProgressUseCase shares pattern)
- **US6 (Event Consumers)**: Depends on US5 (GoalProgressUpdatedEvent must be published)

### Parallel Opportunities per Phase

**Phase 0**: T001–T004 can be done in parallel (different path groups), T005 after all paths, T006 last
**Phase 1**: T007–T010 [P], T011–T013 [P], T014–T016 [P], T017–T019 [P] — all value objects, entities, ports, and exceptions are in different files
**Phase 2**: T020–T024 [P] (US1 use cases), T025–T027 [P] (US2), T028–T029 [P] (US3). T030 and T031 depend on the pattern but are in different files
**Phase 3**: T032–T033 [P] (different repo files). T034 depends on understanding T032 pattern but is a different file
**Phase 4**: T036–T037 [P] (both stubs). T035 is the main consumer
**Phase 5**: T038–T040 are all different files — T039 and T040 can be [P]
**Phase 6**: All unit tests [P] within each sub-group. ITs are sequential (shared DB state)

---

## Implementation Strategy

### MVP First (US1 — Goal CRUD)

1. Phase 0: Add only goal CRUD endpoints (T001, T005 partial, T006)
2. Phase 1: Goal, GoalId, GoalStatus, GoalRepository, GoalNotFoundException (T007, T010, T011, T014, T017)
3. Phase 2: Create/Get/GetAll/Update/Delete use cases (T020–T024)
4. Phase 3: JooqGoalRepository (T032)
5. Phase 5: GoalController (T038 partial), exception handlers (T039), bean wiring (T040 partial)
6. **STOP and VALIDATE**: Test goal CRUD independently

### Incremental Delivery

1. US1 (Goal CRUD) → **independently testable MVP**
2. US2 (Milestones) → adds organizational structure
3. US3 (Habit-Goal Linking) → core differentiator
4. US4 (Manual Progress) → flexibility for all goals
5. US5 (Auto Progress) → event-driven backbone
6. US6 (Event Consumers) → completes pipeline
7. Phase 6 (Tests) → quality gate

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in same phase
- [USx] label maps task to specific user story for traceability
- CHK references map to `checklists/architecture.md` items
- Constitution references ensure compliance with project principles
- Commit granularity: one commit per phase (Constitution §Dev Standards 7)
- KafkaGoalEventPublisher already exists and handles GoalProgressUpdatedEvent — no changes needed
- Partition key for GoalProgressUpdatedEvent = goalId (CHK024/CHK028) — already implemented in existing KafkaGoalEventPublisher
