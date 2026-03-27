# Sprint 2 Quality Checklist: Authentication and User Management

**Purpose**: Validate requirements completeness, clarity, and consistency across security, API contract, architecture, data integrity, and constitution compliance
**Created**: 2026-03-27
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [lifesync-api.yaml](../contracts/lifesync-api.yaml)

## Security — JWT RS256, BCrypt, Token Lifecycle, Banned User Handling

- [ ] CHK001 - Is the JWT access token claims schema fully specified (sub, email, role, iat, exp), or are additional claims left ambiguous? [Completeness, Plan §D4/Data Model §AccessToken]
- [ ] CHK002 - Are requirements for RSA key rotation or expiry documented, or is the assumption that keys are static indefinitely? [Gap, Plan §D1]
- [ ] CHK003 - Is the BCrypt cost factor of 12 justified with a rationale for why not 10 or 14, and are upgrade-path requirements defined if cost needs to change? [Clarity, Spec §FR-004]
- [ ] CHK004 - Are requirements for handling a refresh token that is both revoked AND expired simultaneously specified, or only revoked-only and expired-only cases? [Edge Case, Spec §FR-007/FR-009]
- [ ] CHK005 - Is the anti-enumeration requirement (FR-025) consistent with the registration endpoint returning 409 for duplicate email/username (YAML /auth/register 409)? Login hides which field is wrong, but registration reveals existence. Is this intentional? [Conflict, Spec §FR-025 vs YAML /auth/register]
- [ ] CHK006 - Are requirements specified for what happens when a banned user's still-valid access token is used against the JWT filter — specifically, is the DB lookup frequency (every request vs cached) defined? [Clarity, Plan §D7]
- [ ] CHK007 - Is the refresh token entropy source and minimum length specified (SecureRandom, 32 bytes), or is "opaque string" left underspecified? [Clarity, Plan §D4]
- [ ] CHK008 - Are requirements defined for concurrent refresh token rotation — if two refresh requests arrive simultaneously with the same token, is the second rejected or does it create a race condition? [Edge Case, Spec §FR-007]
- [ ] CHK009 - Is the error message for banned users on login (FR-021) specified as distinct from invalid credentials (FR-025), or could it leak account-banned status to attackers? [Conflict, Spec §FR-021 vs FR-025]
- [ ] CHK010 - Are requirements for expired refresh token cleanup (scheduled deletion from DB) documented, or are revoked/expired tokens retained indefinitely? [Gap, Data Model §RefreshToken]

## API First — OpenAPI YAML Completeness

- [ ] CHK011 - Does the OpenAPI YAML define `required` properties on all response schemas (TokenResponseDto, UserResponseDto, etc.), or are response fields implicitly optional? [Completeness, YAML §components/schemas]
- [ ] CHK012 - Is the `UpdateProfileRequestDto` schema clear that all fields are optional (PATCH semantics) and at least one field must be provided, or could an empty body be submitted? [Clarity, YAML /users/me PATCH]
- [ ] CHK013 - Are error response `message` field values specified per error scenario (e.g., what exact message for 409 duplicate email vs duplicate username), or left implementation-defined? [Clarity, YAML §ErrorResponseDto]
- [ ] CHK014 - Is the admin user list sorting order specified (by what field, ascending/descending), or is it undefined? [Gap, YAML /admin/users GET]
- [ ] CHK015 - Are the 11 endpoints in the YAML consistent with the 11 endpoints listed in the spec (spec says "9 total" in original input, then "11" in stories — which is authoritative)? [Conflict, Spec §User Story 4 vs original input]
- [ ] CHK016 - Is the `search` parameter for admin user listing specified as case-sensitive or case-insensitive partial match? [Clarity, YAML /admin/users GET §search parameter]
- [ ] CHK017 - Are Content-Type requirements specified for all request bodies (only application/json, or also form-urlencoded)? [Completeness, YAML paths]
- [ ] CHK018 - Is the `/auth/logout` 401 response behavior specified — should logout with an already-revoked token return 401 or 204 (idempotent)? [Ambiguity, YAML /auth/logout]

## Hexagonal Architecture — Module Dependency Boundaries

- [ ] CHK019 - Are the allowed import boundaries for each module explicitly listed (domain: pure Java only; application: no jOOQ/Spring MVC; etc.), and does the plan's class placement comply? [Consistency, Constitution §I vs Plan §Project Structure]
- [ ] CHK020 - Is JwtAuthenticationFilter in lifesync-app (not lifesync-web) justified and consistent with the constitution rule that app = "Spring Boot main + top-level config only"? [Conflict, Plan §D6 vs Constitution §I]
- [ ] CHK021 - Are the port interfaces (PasswordEncoder, TokenProvider) in lifesync-domain specified to have zero Spring/framework imports, and is this constraint documented? [Completeness, Constitution §I, Plan §Domain ports]
- [ ] CHK022 - Is the requirement that controllers MUST implement generated interfaces (not hand-written ones) traceable from constitution to plan to YAML? [Traceability, Constitution §II → Plan §D2 → YAML]
- [ ] CHK023 - Is the `@Transactional` placement rule (only in application UseCases) documented per UseCase, specifying which UseCases need transactions and which are read-only? [Clarity, Constitution §Dev Standards 11]
- [ ] CHK024 - Does the plan specify that GlobalExceptionHandler lives in lifesync-web (not lifesync-app), consistent with constitution §I web module definition? [Consistency, Plan §Project Structure vs Constitution §I]

## Data Integrity — Migrations, Refresh Token Rotation, Soft Delete

- [ ] CHK025 - Do the V12 and V13 migration changesets include rollback blocks as required by constitution §V? [Completeness, Constitution §V, Data Model §New Migrations]
- [ ] CHK026 - Is the V12 migration `role` column default value of 'USER' consistent with the domain enum values (USER, ADMIN), and is the constraint that only these two values are allowed enforced at DB or application level? [Consistency, Data Model §V12 vs Spec §FR-011]
- [ ] CHK027 - Are the V12/V13 changeset IDs and filenames consistent with the naming convention `V{n}__{description}.xml`? [Consistency, Constitution §Dev Standards 3]
- [ ] CHK028 - Is the soft-delete behavior fully specified — does `deleted_at` being set also revoke all refresh tokens, or only prevent future logins? [Completeness, Spec §FR-016 vs FR-020]
- [ ] CHK029 - Are requirements for the `updated_at` column specified — should it auto-update on profile changes, ban, or soft delete? [Gap, Data Model §User/UserProfile]
- [ ] CHK030 - Is the user_profiles creation requirement specified — is a profile row created atomically with the user during registration, or lazily on first profile access? [Gap, Spec §FR-001 vs FR-013]
- [ ] CHK031 - Are index requirements for the new `role` column defined, given it will be used in WHERE clauses for admin listing filters? [Gap, Constitution §V.4 vs Data Model §V12]
- [ ] CHK032 - Is the `created_at` column on `refresh_tokens` absent from the schema — how is token age determined for cleanup if only `expires_at` exists? [Gap, Data Model §RefreshToken]

## Constitution Compliance — No Lombok, No Hibernate, Constructor Injection

- [ ] CHK033 - Are the naming conventions for all 11 UseCases consistent with the `{Verb}{Entity}UseCase` pattern? [Consistency, Constitution §Dev Standards 3, Plan §Application Layer]
- [ ] CHK034 - Are the naming conventions for all DTOs consistent with `{Entity}{Action}RequestDto` / `{Entity}ResponseDto` pattern, including the mandatory `Dto` suffix? [Consistency, Constitution §Dev Standards 3, YAML §schemas]
- [ ] CHK035 - Are the test class naming conventions specified — `{Class}Test` for unit, `{Class}IT` for integration — and are all planned test classes listed? [Completeness, Constitution §Dev Standards 3, Plan §Phase 6]
- [ ] CHK036 - Is the requirement for `@DisplayName` on every test method and `@Nested` per method without `@DisplayName` documented in the testing plan? [Completeness, Constitution §IX/X]
- [ ] CHK037 - Are the JaCoCo coverage targets (≥80%) specified with which modules are measured (domain + application only, per constitution) and build-fail behavior? [Clarity, Constitution §X, Plan §Phase 6]
- [ ] CHK038 - Is the MDC requirement (traceId + userId in servlet filter) documented with the specific filter class that sets it (JwtAuthenticationFilter)? [Traceability, Constitution §VIII, Plan §D6]
- [ ] CHK039 - Are the logging level requirements specified per operation (INFO: registration success, login success; WARN: failed login; ERROR: unexpected failure), and is the prohibition on logging passwords/tokens explicit? [Completeness, Constitution §VIII, Plan §Logging]
- [ ] CHK040 - Is the `Clock` injection requirement for time-sensitive logic (token expiry, refresh rotation) documented, or only mentioned for streak logic? [Gap, Constitution §X vs Plan §JWT design]

## Notes

- Items marked with [Conflict] indicate potential contradictions between artifacts that should be resolved before implementation
- Items marked with [Gap] indicate missing requirements that should be added to spec or plan
- Items are numbered CHK001–CHK040 for easy reference in PR reviews
- This checklist validates requirements quality, not implementation correctness
