# Implementation Plan: Authentication and User Management

**Branch**: `002-auth-user-mgmt` | **Date**: 2026-03-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-auth-user-mgmt/spec.md`

## Summary

Implement JWT RS256 authentication (register, login, refresh, logout), user profile management (view, update, Telegram connect, soft-delete), and admin user operations (list, view, ban) across the hexagonal architecture. Requires schema migrations for missing columns (`role`, `telegram_chat_id`), an OpenAPI 3.1 YAML contract (API First), jOOQ repositories, Spring Security filter chain, and 11 REST endpoints exposed through Swagger UI.

## Technical Context

**Language/Version**: Java 21 LTS
**Primary Dependencies**: Spring Boot 3.5.x, Spring Security 6.x, jOOQ 3.19, Liquibase 4.x, nimbus-jose-jwt 9.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0
**Storage**: PostgreSQL 16 (existing via Docker Compose)
**Testing**: JUnit 5 + AssertJ + Mockito + Testcontainers (PostgreSQL)
**Target Platform**: Linux server (Docker)
**Project Type**: Web service (REST API)
**Performance Goals**: Registration-to-login flow < 60s, token refresh < 1s
**Constraints**: No Lombok, no Hibernate, no Spring Data JPA, constructor injection only, all fields final
**Scale/Scope**: Standard web app, 11 endpoints, 3 controllers, 11 UseCases

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | Domain ports → application UseCases → infrastructure adapters → web controllers |
| II | API First | PASS | OpenAPI 3.1 YAML in lifesync-api-spec written before controllers; openapi-generator-maven-plugin generates interfaces |
| III | User Data Isolation | PASS | All jOOQ queries include userId predicate; UseCases validate ownership |
| IV | Single Responsibility | PASS | One UseCase per operation; repositories have no business logic |
| V | Database Migrations via Liquibase | PASS | Two new changesets (V12, V13) for role and telegram_chat_id columns; rollback blocks included |
| VI | Secrets via Environment Variables | PASS | RSA keys via env vars (JWT_PRIVATE_KEY, JWT_PUBLIC_KEY); no hardcoded credentials |
| VII | Portfolio Readability | PASS | No Lombok; explicit Java; English identifiers |
| VIII | Logging Standards | PASS | MDC traceId+userId in JwtAuthenticationFilter; INFO/WARN/ERROR per spec |
| IX | Code Style | PASS | Final fields, constructor injection, explicit constructors, no @Autowired on fields |
| X | Testing Standards | PASS | Unit tests with MockitoExtension; ITs with Testcontainers PostgreSQL; JaCoCo ≥ 80% |
| XI | Code and Documentation Language | PASS | English code/identifiers; Russian commit messages |

## Project Structure

### Documentation (this feature)

```text
specs/002-auth-user-mgmt/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (OpenAPI YAML)
├── checklists/          # Quality checklists
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
lifesync-api-spec/
├── pom.xml                          # openapi-generator-maven-plugin config
└── src/main/resources/
    └── openapi/
        └── lifesync-api.yaml        # OpenAPI 3.1 specification (11 endpoints)

lifesync-domain/
└── src/main/java/ru/zahaand/lifesync/domain/
    └── user/
        ├── User.java                # Entity (id, email, username, passwordHash, role, enabled, deletedAt, timestamps)
        ├── UserProfile.java         # Value object (displayName, timezone, locale, telegramChatId)
        ├── Role.java                # Enum (USER, ADMIN)
        ├── UserId.java              # Typed ID value object
        ├── UserRepository.java      # Port interface
        ├── RefreshTokenRepository.java  # Port interface
        ├── PasswordEncoder.java     # Port interface
        ├── TokenProvider.java       # Port interface
        └── exception/
            ├── UserNotFoundException.java
            ├── DuplicateEmailException.java
            ├── DuplicateUsernameException.java
            ├── InvalidCredentialsException.java
            ├── InvalidTokenException.java
            ├── UserBannedException.java
            └── UserDeletedException.java

lifesync-application/
└── src/main/java/ru/zahaand/lifesync/application/
    └── user/
        ├── RegisterUserUseCase.java
        ├── LoginUserUseCase.java
        ├── RefreshTokenUseCase.java
        ├── LogoutUserUseCase.java
        ├── GetUserProfileUseCase.java
        ├── UpdateUserProfileUseCase.java
        ├── ConnectTelegramUseCase.java
        ├── DeleteUserUseCase.java
        ├── GetAdminUsersUseCase.java
        ├── GetAdminUserUseCase.java
        └── BanUserUseCase.java

lifesync-infrastructure/
├── src/main/java/ru/zahaand/lifesync/infrastructure/
│   └── user/
│       ├── JooqUserRepository.java
│       ├── JooqRefreshTokenRepository.java
│       ├── BcryptPasswordEncoder.java
│       └── JwtTokenProvider.java
└── src/main/resources/db/changelog/
    └── user/
        ├── V1__create_users.xml              # (existing)
        ├── V2__create_user_profiles.xml      # (existing)
        ├── V3__create_refresh_tokens.xml     # (existing)
        ├── V12__add_role_to_users.xml        # NEW: add role column
        └── V13__add_telegram_to_profiles.xml # NEW: add telegram_chat_id column

lifesync-web/
└── src/main/java/ru/zahaand/lifesync/web/
    └── user/
        ├── AuthController.java          # Implements generated AuthApi interface
        ├── UserController.java          # Implements generated UserApi interface
        ├── AdminController.java         # Implements generated AdminApi interface
        ├── JwtAuthenticationFilter.java # OncePerRequestFilter: extract + validate + SecurityContext
        └── GlobalExceptionHandler.java

lifesync-app/
├── src/main/java/ru/zahaand/lifesync/app/
│   └── config/
│       ├── SecurityConfig.java          # (modify existing) JWT filter chain, role-based rules
│       └── OpenApiConfig.java           # SpringDoc: bearer auth scheme, API info
└── src/main/resources/
    └── application.yml                  # (modify existing) add JWT config properties
```

**Structure Decision**: Extends existing Maven multi-module hexagonal layout. No new modules needed — all code fits into the 6 existing modules following constitution rule I.

## Complexity Tracking

> No constitution violations. No complexity overrides needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Design Decisions

### D1: JWT Library — nimbus-jose-jwt

**Decision**: Use nimbus-jose-jwt (com.nimbusds:nimbus-jose-jwt) for RS256 JWT operations.
**Rationale**: Spring Security's built-in OAuth2 Resource Server module bundles nimbus-jose-jwt. Using it directly avoids adding another JWT library. Well-maintained, widely used, supports RSA key loading from PEM.
**Alternative rejected**: io.jsonwebtoken:jjwt — would add a second JWT library alongside the one already transitively included.

### D2: API-Spec Module Conversion from POM to JAR

**Decision**: Convert lifesync-api-spec from `<packaging>pom</packaging>` to `<packaging>jar</packaging>` and add openapi-generator-maven-plugin to generate Java interfaces from the OpenAPI YAML.
**Rationale**: The generated interfaces must be compiled as a JAR so lifesync-web can depend on lifesync-api-spec and implement them. POM packaging cannot compile Java sources.
**Impact**: lifesync-web pom.xml gains dependency on lifesync-api-spec.

### D3: Schema Migrations — Additive Changesets Only

**Decision**: Add two new Liquibase changesets (V12, V13) rather than modifying existing V1/V2 changesets.
**Rationale**: Constitution V mandates "Applied changesets MUST NOT be modified." V1 and V2 were applied in Sprint 1. Adding columns via separate addColumn changesets is the correct migration path.

### D4: Refresh Token Hash Storage

**Decision**: Store refresh token as SHA-256 hash in `token_hash` column (existing schema). Generate a random opaque string (UUID or SecureRandom), return the raw value to the client, store only the hash.
**Rationale**: If the database is compromised, hashed tokens cannot be used to authenticate. SHA-256 is appropriate because refresh tokens have high entropy (unlike passwords which need BCrypt).

### D5: Login Identifier Detection

**Decision**: Detect whether the login identifier is email or username by checking for the `@` character. If it contains `@`, treat as email; otherwise treat as username.
**Rationale**: Simple, unambiguous heuristic. Usernames are constrained to `[a-z0-9_-]` (no `@` allowed), so there's no overlap.

### D6: JwtAuthenticationFilter Placement

**Decision**: Place `JwtAuthenticationFilter` as `OncePerRequestFilter` in lifesync-web (web layer) registered before `UsernamePasswordAuthenticationFilter` in the SecurityConfig filter chain. SecurityConfig (filter chain wiring) remains in lifesync-app.
**Rationale**: The filter is an HTTP-layer concern (extracts Bearer header, sets MDC) and belongs in lifesync-web per constitution §I. SecurityConfig in lifesync-app wires the filter into the chain as a top-level configuration concern.

### D7: Banned User Enforcement

**Decision**: Banned user check is deferred from JwtAuthenticationFilter. Banned users lose access within ≤15 minutes via access token expiry. Refresh token is revoked immediately on ban (BanUserUseCase). Full request-level check deferred to Sprint 7.
**Rationale**: Adding a DB lookup on every authenticated request is premature for current scale. The 15-minute access token window is an acceptable trade-off. BanUserUseCase already revokes all refresh tokens, ensuring no new access tokens can be obtained. A per-request check with Caffeine cache can be added in Sprint 7 (Observability) when caching infrastructure is in place.

### D8: SpringDoc OpenAPI Integration

**Decision**: Use springdoc-openapi-starter-webmvc-ui (SpringDoc 2.x) for Swagger UI, reading the same OpenAPI YAML from lifesync-api-spec.
**Rationale**: SpringDoc 2.x is the Spring Boot 3.x compatible OpenAPI documentation tool. It can serve the static YAML and overlay runtime-generated schema annotations.

## Dependency Changes

### Parent pom.xml — New version properties

```xml
<nimbus-jose-jwt.version>9.47</nimbus-jose-jwt.version>
<springdoc.version>2.8.6</springdoc.version>
<openapi-generator.version>7.12.0</openapi-generator.version>
<testcontainers.version>1.20.6</testcontainers.version>
```

### lifesync-api-spec/pom.xml

- Change packaging from `pom` to `jar`
- Add: spring-boot-starter-web (compile, for generated interfaces)
- Add: spring-boot-starter-validation (compile, for @Valid annotations)
- Add: openapi-generator-maven-plugin (build plugin)
- Add: jackson-databind-nullable (for OpenAPI generated code)

### lifesync-domain/pom.xml

- No new dependencies (pure Java)

### lifesync-application/pom.xml

- Add: commons-lang3 (for StringUtils validation)

### lifesync-infrastructure/pom.xml

- Add: nimbus-jose-jwt (for JwtTokenProvider)
- Add: spring-security-crypto (for BcryptPasswordEncoder — already transitive via lifesync-web, but explicit is better)

### lifesync-web/pom.xml

- Add: lifesync-api-spec (to implement generated interfaces)
- Add: springdoc-openapi-starter-webmvc-ui

### lifesync-app/pom.xml

- Add: testcontainers + testcontainers-postgresql + testcontainers-junit-jupiter (test scope)

## Implementation Phases

### Phase 1: Schema & Domain Layer

1. **V12 migration**: Add `role` column to `users` table (varchar(20), NOT NULL, default 'USER')
2. **V13 migration**: Add `telegram_chat_id` column to `user_profiles` table (varchar(50), nullable)
3. **Domain entities**: User, UserProfile, Role enum, UserId value object
4. **Domain ports**: UserRepository, RefreshTokenRepository, PasswordEncoder, TokenProvider
5. **Domain exceptions**: 7 exception classes

### Phase 2: OpenAPI Contract & Code Generation

1. **OpenAPI 3.1 YAML**: Define all 11 endpoints with request/response schemas
2. **lifesync-api-spec pom.xml**: Configure openapi-generator-maven-plugin to generate Spring interfaces
3. **Verify**: `mvn clean compile -pl lifesync-api-spec` produces Java interfaces

### Phase 3: Application Layer (UseCases)

1. **Auth UseCases**: RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase, LogoutUserUseCase
2. **User UseCases**: GetUserProfileUseCase, UpdateUserProfileUseCase, ConnectTelegramUseCase, DeleteUserUseCase
3. **Admin UseCases**: GetAdminUsersUseCase, GetAdminUserUseCase, BanUserUseCase
4. Each UseCase injects domain ports only; `@Transactional` where mutations occur

### Phase 4: Infrastructure Layer

1. **JooqUserRepository**: CRUD operations on users + user_profiles tables
2. **JooqRefreshTokenRepository**: Token hash storage, lookup, revocation, bulk revocation by userId
3. **BcryptPasswordEncoder**: BCrypt cost=12 implementation of PasswordEncoder port
4. **JwtTokenProvider**: RS256 access token generation/validation using nimbus-jose-jwt; refresh token generation (SecureRandom + SHA-256 hash)

### Phase 5: Web Layer & Security

1. **Controllers**: AuthController, UserController, AdminController implementing generated interfaces
2. **GlobalExceptionHandler**: Map domain exceptions → HTTP status codes (400, 401, 403, 404, 409)
3. **JwtAuthenticationFilter**: Extract Bearer token, validate, set SecurityContext, MDC (traceId + userId)
4. **SecurityConfig**: Update filter chain — public auth endpoints, ADMIN role for admin endpoints, authenticated for user endpoints
5. **OpenApiConfig**: SpringDoc bearer auth scheme, API metadata
6. **application.yml**: JWT key paths, token TTL properties

### Phase 6: Testing

1. **Unit tests**: All 11 UseCases with MockitoExtension
2. **Integration tests**: AuthControllerIT, UserControllerIT, AdminControllerIT with Testcontainers PostgreSQL
3. **JaCoCo config**: Verify ≥ 80% coverage on domain + application modules
