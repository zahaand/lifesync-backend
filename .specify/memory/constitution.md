<!--
  Sync Impact Report
  ==================
  Version change: 0.0.0 (template) → 1.1.0
  Bump rationale: MINOR — first substantive adoption with 11 principles,
                  technology stack, and development standards.

  Modified principles:
    - Template placeholders replaced with 11 concrete principles (I–XI).

  Added sections:
    - Core Principles: I. Hexagonal Architecture, II. API First,
      III. User Data Isolation, IV. Single Responsibility,
      V. Database Migrations via Liquibase, VI. Secrets via Environment Variables,
      VII. Portfolio Readability, VIII. Logging Standards, IX. Code Style,
      X. Testing Standards, XI. Code and Documentation Language.
    - Technology Stack (replaces template [SECTION_2_NAME]).
    - Development Standards (replaces template [SECTION_3_NAME]).
    - Governance with amendment procedure.

  Removed sections:
    - All template placeholders and HTML example comments.

  Templates requiring updates:
    - .specify/templates/plan-template.md        ✅ compatible (Constitution Check
      section is generic — gates derived at plan time from this file)
    - .specify/templates/spec-template.md         ✅ compatible (no constitution-
      specific placeholders; user stories and requirements are generic)
    - .specify/templates/tasks-template.md        ✅ compatible (phase structure
      and task format are project-agnostic)
    - .specify/templates/commands/*.md            ✅ no command files present

  Follow-up TODOs: none.
-->

# LifeSync Backend Constitution

## Core Principles

### I. Hexagonal Architecture (NON-NEGOTIABLE)

The application MUST follow Hexagonal Architecture (Ports & Adapters).
Dependencies MUST point strictly inward: infrastructure → application → domain.

- **domain**: pure Java only. NO Spring, jOOQ, Kafka, Jackson imports.
- **application**: UseCases only. NO jOOQ, Kafka, Spring MVC imports.
- **infrastructure**: jOOQ repos, Kafka adapters, Telegram adapter, Liquibase.
- **web**: controllers (implement generated interfaces), DTOs,
  GlobalExceptionHandler.
- **app**: Spring Boot main + top-level config only.

Direct controller → repository calls are PROHIBITED.

### II. API First (NON-NEGOTIABLE)

OpenAPI 3.1 YAML in lifesync-api-spec is the single source of truth.
YAML MUST be written before controller implementation.
Controller interfaces MUST be generated via openapi-generator-maven-plugin.
Hand-written controller interfaces are PROHIBITED.

### III. User Data Isolation (NON-NEGOTIABLE)

All jOOQ queries MUST include userId predicate.
UseCase MUST validate resource ownership before any mutation.
If ownership fails → throw AccessDeniedException.

### IV. Single Responsibility (SRP)

One class = one clearly stated responsibility.
UseCase MUST NOT send Telegram notifications — that is the consumer's job.
jOOQ repository MUST NOT contain business logic.

### V. Database Migrations via Liquibase (NON-NEGOTIABLE)

All schema changes via Liquibase only. Direct DDL is PROHIBITED.
Files organised under `db/changelog/{domain}/`.
Every changeset MUST have a rollback block.
Applied changesets MUST NOT be modified.
XML comments in migration files are PROHIBITED.

### VI. Secrets via Environment Variables

No hardcoded credentials anywhere. `.env` in `.gitignore`.

### VII. Portfolio Readability (NON-NEGOTIABLE)

YAGNI: no speculative features.
No Lombok. All boilerplate is explicit Java.
Loggers: `private static final Logger log = LoggerFactory.getLogger(Cls.class);`
All identifiers in English.

### VIII. Logging Standards (NON-NEGOTIABLE)

Logger via `LoggerFactory.getLogger()`. No Lombok.

- **DEBUG**: params, steps.
- **INFO**: business success.
- **WARN**: retry/skip.
- **ERROR**: failure.

MDC must contain `traceId` and `userId` (set in servlet filter).
Kafka consumers log topic, partition, offset at DEBUG before processing.
Sensitive data (passwords, tokens) MUST NOT be logged.

### IX. Code Style (NON-NEGOTIABLE)

- Member order: dependencies → helpers → constants → constructors → methods.
- All fields final. Constructor injection only. No `@Autowired` on fields.
- All constructors explicit. No more than 1 consecutive blank line.
- No null from public methods — use `Optional` or throw.
- Curly braces always, including single-line `if`/`for`.
- `var` IS PERMITTED when type is unambiguously clear from the RHS.
- `var` is PROHIBITED when type requires inspecting another file.
- Private methods start with a verb.
- `@DisplayName` is first annotation on test methods.
- `@Nested` test classes have no `@DisplayName`.

### X. Testing Standards (NON-NEGOTIABLE)

- Unit: `@ExtendWith(MockitoExtension.class)`. No Spring context.
  No `@SpringBootTest`.
- Integration tests: suffix IT (e.g. `HabitControllerIT`).
- Testcontainers for PG + Kafka. Shared `BaseIT` with `@Container static`.
- `@Nested` per method. `@DisplayName` in English on every test.
- Boundary cases: `@ParameterizedTest` + `@MethodSource`.
- JaCoCo ≥ 80 % on domain + application. Build MUST fail if below.
- Time-sensitive logic (streak): always inject `Clock.fixed()`,
  never `Instant.now()`.

### XI. Code and Documentation Language

- All code, identifiers, comments, Javadoc: English only.
- `README.md`: bilingual (English primary, Russian translation).
- Commit messages: English, Conventional Commits.

## Technology Stack

| Concern | Choice |
|---------|--------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.5.x |
| Build | Maven Multi-Module |
| Database | PostgreSQL 16 |
| SQL | jOOQ 3.19 (NO Hibernate, NO Spring Data JPA) |
| Migrations | Liquibase 4.x |
| Broker | Spring Kafka 3.x |
| Security | Spring Security 6.x + JWT RS256 |
| Cache | Spring Cache + Caffeine (Redis-ready) |
| API docs | SpringDoc OpenAPI 2.x |
| Metrics | Micrometer + Prometheus |
| Notifications | Telegram Bot (TelegramBots library) |
| Tests | JUnit 5 + AssertJ + Mockito + Testcontainers |
| Coverage | JaCoCo ≥ 80 % |
| Quality | Checkstyle + SpotBugs |

## Development Standards

1. **Maven modules** (canonical order):
   `lifesync-api-spec` → `lifesync-domain` → `lifesync-application` →
   `lifesync-infrastructure` → `lifesync-web` → `lifesync-app`.
   No new modules without amendment.
2. **Package pattern**: `ru.zahaand.lifesync.{module}.{domain}`
3. **Naming**:
   - UseCases: `{Verb}{Entity}UseCase`
   - Ports: `{Entity}Repository`, `{Entity}EventPublisher`
   - Events: `{Entity}{PastVerb}Event`
   - Topics: `{domain}.{entity}.{verb}`
   - Consumers: `{Purpose}Consumer`
   - DTOs: `{Entity}{Action}RequestDto`, `{Entity}ResponseDto`
     (Dto suffix mandatory)
   - Liquibase: `V{n}__{description}.sql`
   - Tests: `{Class}Test` (unit), `{Class}IT` (integration)
   - Test methods: `should{Result}When{Condition}`
4. **Utility classes**: `Utils` suffix + private no-arg constructor.
5. No orphaned code before commit.
6. **Local run**: `docker compose up -d` + `.env` only.
7. **Commits**: Conventional Commits.
   `feat` / `fix` / `refactor` / `test` / `docs` / `chore` / `perf` / `ci`.
8. API change = YAML change first. PR without YAML update is PROHIBITED.
9. Kafka topic/event changes: atomic PR (producers + consumers together).
10. Kafka consumers: check `processed_events` for idempotency.
    Duplicates → WARN, not error.
11. `@Transactional`: ONLY in application module UseCases.
12. `commons-lang3`: `StringUtils` for string checks,
    `CollectionUtils` for collection checks.
    Must be explicit dependency in parent `pom.xml`.

## Governance

**Amendment procedure**:

1. State motivation.
2. Version bump: MAJOR = breaking, MINOR = new principle,
   PATCH = clarification.
3. Update `LAST_AMENDED_DATE`.
4. Record in Sync Impact Report.

**Compliance**: Claude Code and all reviews MUST verify Core Principles.
Violations logged in `plan.md` Complexity Tracking with justification.

**Version**: 1.1.0 | **Ratified**: 2026-03-27 | **Last Amended**: 2026-03-27
