# Tasks: Authentication and User Management

**Input**: Design documents from `/specs/002-auth-user-mgmt/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Unit tests for all UseCases + integration tests for all controllers (per constitution X and user request).

**Organization**: Tasks grouped by user story (P1–P4) with shared foundational phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Exact file paths included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven dependency updates, schema migrations, and OpenAPI code generation — must complete before any application code.

- [ ] T001 Add version properties (nimbus-jose-jwt, springdoc, openapi-generator, testcontainers) to `pom.xml` `<properties>` section
- [ ] T002 Add commons-lang3 dependency to `lifesync-application/pom.xml`
- [ ] T003 [P] Add nimbus-jose-jwt and spring-security-crypto dependencies to `lifesync-infrastructure/pom.xml`
- [ ] T004 [P] Add lifesync-api-spec and springdoc-openapi-starter-webmvc-ui dependencies to `lifesync-web/pom.xml`
- [ ] T005 [P] Add nimbus-jose-jwt dependency and testcontainers (test scope) to `lifesync-app/pom.xml`
- [ ] T006 [P] Create Liquibase migration `lifesync-infrastructure/src/main/resources/db/changelog/user/V12__add_role_to_users.xml` — addColumn `role` varchar(20) NOT NULL default 'USER' with rollback block
- [ ] T007 [P] Create Liquibase migration `lifesync-infrastructure/src/main/resources/db/changelog/user/V13__add_telegram_to_profiles.xml` — addColumn `telegram_chat_id` varchar(50) nullable with rollback block
- [ ] T008 Add V12 and V13 includes to `lifesync-infrastructure/src/main/resources/db/changelog/db.changelog-master.xml`
- [ ] T009 Add JWT configuration properties to `lifesync-app/src/main/resources/application.yml` — jwt.private-key, jwt.public-key, jwt.access-token-expiry (900), jwt.refresh-token-expiry (604800) from environment variables
- [ ] T010 Add .env.example entries for JWT_PRIVATE_KEY and JWT_PUBLIC_KEY

**Checkpoint**: All dependencies resolved, migrations ready, environment configured.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: OpenAPI contract, domain model, ports, and infrastructure adapters — MUST complete before any user story implementation.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### OpenAPI Contract & Code Generation

- [ ] T011 Copy OpenAPI YAML from `specs/002-auth-user-mgmt/contracts/lifesync-api.yaml` to `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml`
- [ ] T012 Configure `lifesync-api-spec/pom.xml` — change packaging from `pom` to `jar`, add spring-boot-starter-web, spring-boot-starter-validation, jackson-databind-nullable dependencies, configure openapi-generator-maven-plugin with: generator=spring, interfaceOnly=true, useSpringBoot3=true, useTags=true, skipDefaultInterface=true, apiPackage=ru.zahaand.lifesync.api, modelPackage=ru.zahaand.lifesync.api.model
- [ ] T013 Verify code generation: run `./mvnw clean compile -pl lifesync-api-spec` and confirm AuthApi, UserApi, AdminApi interfaces are generated

### Domain Layer (lifesync-domain — pure Java, NO Spring imports)

- [ ] T014 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/Role.java` — enum with USER, ADMIN values
- [ ] T015 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/UserId.java` — record value object wrapping UUID, null-check in compact constructor
- [ ] T016 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/UserProfile.java` — value object with displayName, timezone, locale, telegramChatId fields
- [ ] T017 Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/User.java` — entity with id (UserId), email, username, passwordHash, role (Role), enabled, createdAt, updatedAt, deletedAt. Methods: ban(), softDelete(), isActive(), isBanned(), isDeleted(). Constructor injection, all fields final, no Lombok
- [ ] T018 [P] Create domain exceptions in `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/exception/` — UserNotFoundException, DuplicateEmailException, DuplicateUsernameException, InvalidCredentialsException, InvalidTokenException, UserBannedException, UserDeletedException. All extend RuntimeException with message constructor
- [ ] T019 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/PasswordEncoder.java` — port interface with encode(String) and matches(String, String) methods
- [ ] T020 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/TokenProvider.java` — port interface with generateAccessToken(User), generateRefreshToken(), validateAccessToken(String) methods. Define TokenClaims and TokenPair as inner records
- [ ] T021 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/UserRepository.java` — port interface with findById, findByEmail, findByUsername, existsByEmail, existsByUsername, save, update methods. Return Optional where applicable
- [ ] T022 [P] Create `lifesync-domain/src/main/java/ru/zahaand/lifesync/domain/user/RefreshTokenRepository.java` — port interface with save(UserId, String tokenHash, Instant expiresAt), findByTokenHash(String), revokeByTokenHash(String), revokeAllByUserId(UserId) methods

### Infrastructure Layer (lifesync-infrastructure)

- [ ] T023 Implement `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/BcryptPasswordEncoder.java` — implements PasswordEncoder port, BCrypt cost=12, @Component. Constructor injection, all fields final
- [ ] T024 Implement `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/JwtTokenProvider.java` — implements TokenProvider port, RS256 signing/validation via nimbus-jose-jwt. Load RSA keys from @Value properties (PEM-encoded). Clock injection for expiry. generateRefreshToken: SecureRandom 32 bytes → Base64URL, SHA-256 hash. @Component
- [ ] T025 Implement `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/JooqUserRepository.java` — implements UserRepository port using jOOQ DSLContext. All queries include userId predicate where applicable (constitution III). Case-insensitive username lookup via LOWER(). Save creates user + user_profiles row atomically. @Repository
- [ ] T026 Implement `lifesync-infrastructure/src/main/java/ru/zahaand/lifesync/infrastructure/user/JooqRefreshTokenRepository.java` — implements RefreshTokenRepository port using jOOQ DSLContext. Stores token_hash (SHA-256), supports bulk revocation by userId. @Repository

### Web Layer (Shared Components)

- [ ] T027 Implement `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/GlobalExceptionHandler.java` — @RestControllerAdvice mapping domain exceptions to HTTP responses: UserNotFoundException→404, DuplicateEmailException→409, DuplicateUsernameException→409, InvalidCredentialsException→401, InvalidTokenException→401, UserBannedException→403 (message "Account is disabled" per FR-026), UserDeletedException→401, MethodArgumentNotValidException→400. Standard ErrorResponseDto format: timestamp, status, error, message, path
- [ ] T028 Implement `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/JwtAuthenticationFilter.java` — OncePerRequestFilter in lifesync-web (per spec assumption). Extract Bearer token from Authorization header, validate via TokenProvider port, load user from UserRepository, check enabled flag (403 if banned per FR-026), set SecurityContext with UsernamePasswordAuthenticationToken (role as GrantedAuthority). Set MDC: traceId (UUID), userId. Clear MDC in finally block. Skip filter for public paths (/api/v1/auth/**, /swagger-ui/**, /v3/api-docs/**)

### Test Infrastructure

- [ ] T029 Create `lifesync-app/src/test/java/ru/zahaand/lifesync/app/BaseIT.java` — abstract base class for integration tests with @Container static PostgreSQLContainer, @DynamicPropertySource for datasource config. All *IT classes extend this

### Security & Application Configuration

- [ ] T030 Update `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/SecurityConfig.java` — replace permissive config with: CSRF disabled, stateless session, JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter, permit /api/v1/auth/**, /swagger-ui/**, /v3/api-docs/**, require ADMIN role for /api/v1/admin/**, require authenticated for all other /api/v1/** paths

**Checkpoint**: Foundation ready — OpenAPI interfaces generated, domain model complete, infrastructure adapters implemented, security filter chain configured, BaseIT ready. User story implementation can begin.

---

## Phase 3: User Story 1 — User Registration and Login (Priority: P1) 🎯 MVP

**Goal**: Users can register, log in, refresh tokens, and log out. Full JWT RS256 authentication lifecycle.

**Independent Test**: Register → login → receive tokens → refresh → logout. All auth endpoints work without any other user story.

### Application Layer (UseCases)

- [ ] T031 [US1] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/RegisterUserUseCase.java` — validate email format, username constraints (3–32 chars, [a-z0-9_-]), password strength (min 8, 1 upper, 1 lower, 1 digit) via commons-lang3 StringUtils. Check existsByEmail/existsByUsername (throw DuplicateEmailException/DuplicateUsernameException). Hash password via PasswordEncoder port. Save user with role=USER. Create user_profiles row. @Transactional. Log INFO on success
- [ ] T032 [US1] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/LoginUserUseCase.java` — detect identifier type by @ presence (email vs username). Look up user via UserRepository. Check deleted_at (throw UserDeletedException). Check enabled (throw UserBannedException). Verify password via PasswordEncoder.matches(). Generate access + refresh tokens via TokenProvider. Store refresh token hash via RefreshTokenRepository. Log INFO on success, WARN on failed attempt. Clock injection for token expiry
- [ ] T033 [US1] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/RefreshTokenUseCase.java` — look up refresh token by SHA-256 hash. Validate not revoked, not expired (Clock injection). Revoke old token. Check user enabled (throw UserBannedException). Generate new access + refresh token pair. Store new hash. @Transactional. Log INFO on success
- [ ] T034 [US1] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/LogoutUserUseCase.java` — look up refresh token by hash, revoke it. Idempotent: no error if already revoked. Log INFO on success

### Unit Tests (alongside UseCases)

- [ ] T035 [P] [US1] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/RegisterUserUseCaseTest.java` — @ExtendWith(MockitoExtension.class), @Nested per method, @DisplayName in English. Test: successful registration, duplicate email, duplicate username, invalid email, weak password, username too short/long, username invalid chars
- [ ] T036 [P] [US1] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/LoginUserUseCaseTest.java` — test: login with email, login with username, wrong password, nonexistent user, banned user, deleted user. Use Clock.fixed()
- [ ] T037 [P] [US1] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/RefreshTokenUseCaseTest.java` — test: successful rotation, revoked token, expired token, banned user on refresh. Use Clock.fixed()
- [ ] T038 [P] [US1] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/LogoutUseCaseTest.java` — test: successful logout, already-revoked token (idempotent), invalid token

### Web Layer (Controller)

- [ ] T039 [US1] Implement `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/AuthController.java` — implements generated AuthApi interface. Inject 4 auth UseCases. Map DTOs to/from domain objects. POST /auth/register → 201, POST /auth/login → 200, POST /auth/refresh → 200, POST /auth/logout → 204

### Integration Test

- [ ] T040 [US1] Write `lifesync-app/src/test/java/ru/zahaand/lifesync/app/AuthControllerIT.java` — @SpringBootTest with Testcontainers PostgreSQL. Test full flows: register + login, login with email vs username, duplicate registration (409), invalid credentials (401), refresh rotation, logout revocation, refresh with revoked token (401), banned user login (403), deleted user login (401). Suffix IT per constitution

### Verification

- [ ] T041 [US1] Verify User Story 1: run `./mvnw clean verify` — all auth unit tests pass, AuthControllerIT passes, register→login→refresh→logout flow works end-to-end

**Checkpoint**: Authentication lifecycle fully functional. MVP complete.

---

## Phase 4: User Story 2 — User Profile Management (Priority: P2)

**Goal**: Authenticated users can view profile, update profile fields, connect Telegram, and soft-delete their account.

**Independent Test**: Login → view profile → update display name/timezone → connect Telegram → delete account.

### Application Layer (UseCases)

- [ ] T042 [P] [US2] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/GetUserProfileUseCase.java` — load user + profile by userId (from SecurityContext). Return combined data. Throw UserNotFoundException if not found
- [ ] T043 [P] [US2] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/UpdateUserProfileUseCase.java` — validate timezone (IANA via ZoneId.of()), validate locale (BCP 47). Update only provided fields (PATCH semantics). @Transactional. Log INFO on success
- [ ] T044 [P] [US2] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/ConnectTelegramUseCase.java` — store telegramChatId on user profile. @Transactional. Log INFO on success
- [ ] T045 [P] [US2] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/DeleteUserUseCase.java` — set deleted_at timestamp on user (Clock injection). Revoke all refresh tokens for user. @Transactional. Log INFO on success

### Unit Tests (alongside UseCases)

- [ ] T046 [P] [US2] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/GetUserProfileUseCaseTest.java` — test: existing user, user not found
- [ ] T047 [P] [US2] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/UpdateUserProfileUseCaseTest.java` — test: update all fields, update single field, invalid timezone, invalid locale
- [ ] T048 [P] [US2] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/ConnectTelegramUseCaseTest.java` — test: successful connect, overwrite existing chatId
- [ ] T049 [P] [US2] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/DeleteUserUseCaseTest.java` — test: successful soft delete, user not found. Use Clock.fixed()

### Web Layer (Controller)

- [ ] T050 [US2] Implement `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/UserController.java` — implements generated UserApi interface. Inject 4 user UseCases. Extract userId from SecurityContext. GET /users/me → 200, PATCH /users/me → 200, PUT /users/me/telegram → 200, DELETE /users/me → 204

### Integration Test

- [ ] T051 [US2] Write `lifesync-app/src/test/java/ru/zahaand/lifesync/app/UserControllerIT.java` — @SpringBootTest with Testcontainers. Test: view profile, update profile, connect Telegram, delete account, access without token (401), invalid timezone (400). Register + login first to get token

### Verification

- [ ] T052 [US2] Verify User Story 2: run `./mvnw clean verify` — all user profile unit tests pass, UserControllerIT passes

**Checkpoint**: Profile management fully functional. Users can self-manage their accounts.

---

## Phase 5: User Story 3 — Admin User Management (Priority: P3)

**Goal**: Admins can list all users (with filter/search), view any user, and ban users (revoking refresh tokens).

**Independent Test**: Login as admin → list users → filter by status → search by email → view user → ban user → verify banned user cannot log in.

### Application Layer (UseCases)

- [ ] T053 [P] [US3] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/GetAdminUsersUseCase.java` — paginated listing (page 0-indexed, size default 20, max 100). Filter by status (active/banned/deleted). Search by email or username (case-insensitive partial match). Return page with totalElements, totalPages
- [ ] T054 [P] [US3] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/GetAdminUserUseCase.java` — find user by UUID id. Include profile data. Throw UserNotFoundException if not found
- [ ] T055 [US3] Implement `lifesync-application/src/main/java/ru/zahaand/lifesync/application/user/BanUserUseCase.java` — set enabled=false on user. Revoke all refresh tokens via RefreshTokenRepository.revokeAllByUserId(). Throw UserNotFoundException if target not found. @Transactional. Log INFO on ban

### Unit Tests (alongside UseCases)

- [ ] T056 [P] [US3] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/GetAdminUsersUseCaseTest.java` — test: no filters, filter by active, filter by banned, filter by deleted, search by email, search by username, pagination
- [ ] T057 [P] [US3] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/GetAdminUserUseCaseTest.java` — test: existing user, user not found
- [ ] T058 [P] [US3] Write `lifesync-application/src/test/java/ru/zahaand/lifesync/application/user/BanUserUseCaseTest.java` — test: successful ban, user not found, ban another admin

### Web Layer (Controller)

- [ ] T059 [US3] Implement `lifesync-web/src/main/java/ru/zahaand/lifesync/web/user/AdminController.java` — implements generated AdminApi interface. Inject 3 admin UseCases. GET /admin/users → 200, GET /admin/users/{id} → 200, POST /admin/users/{id}/ban → 200

### Integration Test

- [ ] T060 [US3] Write `lifesync-app/src/test/java/ru/zahaand/lifesync/app/AdminControllerIT.java` — @SpringBootTest with Testcontainers. Seed admin user (direct DB insert). Test: list users (paginated), filter by status, search, view user by id, ban user, banned user cannot login, non-admin gets 403, ban nonexistent user (404)

### Verification

- [ ] T061 [US3] Verify User Story 3: run `./mvnw clean verify` — all admin unit tests pass, AdminControllerIT passes

**Checkpoint**: Admin governance fully functional.

---

## Phase 6: User Story 4 — Interactive API Documentation (Priority: P4)

**Goal**: Swagger UI available at /swagger-ui.html with all 11 endpoints, Authorize button for JWT bearer token.

**Independent Test**: Open Swagger UI in browser → see all 11 endpoints → use Authorize button → test endpoints.

### Implementation

- [ ] T062 [US4] Implement `lifesync-app/src/main/java/ru/zahaand/lifesync/app/config/OpenApiConfig.java` — @Configuration with @OpenAPIDefinition: title "LifeSync API", version "0.1.0", description. @SecurityScheme: type=HTTP, scheme=bearer, bearerFormat=JWT, name=bearerAuth. Ensure Swagger UI accessible without authentication (already configured in SecurityConfig T030)

### Verification

- [ ] T063 [US4] Verify User Story 4: start application, open http://localhost:8080/swagger-ui.html — confirm all 11 endpoints visible grouped by Auth/User/Admin tags, Authorize button works, public endpoints callable without token

**Checkpoint**: API documentation complete. All 4 user stories delivered.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Coverage validation and final verification.

- [ ] T064 Configure JaCoCo in `pom.xml` — enforce ≥ 80% coverage on lifesync-domain and lifesync-application modules, build fails if below threshold
- [ ] T065 Run `./mvnw clean verify` — all unit tests pass, all integration tests pass, JaCoCo thresholds met, application starts successfully
- [ ] T066 Run quickstart.md smoke test — execute all curl commands from `specs/002-auth-user-mgmt/quickstart.md` against running application

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Phase 2 completion
  - US1 (Phase 3): No dependencies on other stories
  - US2 (Phase 4): No dependencies on other stories (but benefits from US1 auth flow for IT tests)
  - US3 (Phase 5): No dependencies on other stories (but benefits from US1 auth flow for IT tests)
  - US4 (Phase 6): No dependencies (Swagger UI reads from existing OpenAPI YAML + controllers)
- **Polish (Phase 7)**: Depends on all user stories being complete

### Within Each User Story

- UseCases before controllers (domain → application → web)
- Unit tests alongside UseCases (same phase, parallel where possible)
- Controllers after UseCases
- Integration tests after controllers
- Verification last

### Parallel Opportunities

- T003/T004/T005: Module POM updates (different files)
- T006/T007: Migrations (different files)
- T014/T015/T016: Domain value objects (different files)
- T018/T019/T020/T021/T022: Domain exceptions + ports (different files)
- T035/T036/T037/T038: US1 unit tests (different files)
- T042/T043/T044/T045: US2 UseCases (different files)
- T046/T047/T048/T049: US2 unit tests (different files)
- T053/T054: US3 list + view UseCases (different files)
- T056/T057/T058: US3 unit tests (different files)

---

## Parallel Example: User Story 1

```bash
# Launch all US1 unit tests in parallel (after UseCases):
Task: T035 "RegisterUserUseCaseTest.java"
Task: T036 "LoginUserUseCaseTest.java"
Task: T037 "RefreshTokenUseCaseTest.java"
Task: T038 "LogoutUseCaseTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T010)
2. Complete Phase 2: Foundational (T011–T030)
3. Complete Phase 3: User Story 1 (T031–T041)
4. **STOP and VALIDATE**: Register → login → refresh → logout works end-to-end
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → MVP! (auth works)
3. Add User Story 2 → Test independently → Profile management works
4. Add User Story 3 → Test independently → Admin governance works
5. Add User Story 4 → Test independently → Swagger UI works
6. Polish → Coverage validated, smoke tested

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- JwtAuthenticationFilter placed in lifesync-web per spec assumption (not lifesync-app)
- SecurityConfig wiring in lifesync-app per constitution (app = top-level config)
- BaseIT (T029) is in Phase 2 (Foundational), ready before any integration tests
- Clock.fixed() required in: LoginUserUseCase, RefreshTokenUseCase, DeleteUserUseCase, JwtTokenProvider
- All 11 UseCases follow {Verb}{Entity}UseCase naming convention
- All DTOs follow {Entity}{Action}RequestDto / {Entity}ResponseDto with mandatory Dto suffix
