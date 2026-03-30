# Research: Authentication and User Management

**Feature**: 002-auth-user-mgmt | **Date**: 2026-03-27

## R1: JWT RS256 Library Selection

**Decision**: nimbus-jose-jwt 9.x (com.nimbusds:nimbus-jose-jwt)
**Rationale**: Already a transitive dependency of Spring Security's OAuth2 Resource Server. Mature library with first-class RSA support, PEM key parsing, and JWS signing/verification. Using it directly avoids adding a competing library (jjwt).
**Alternatives considered**:
- io.jsonwebtoken:jjwt (0.12.x) — popular but would add a second JWT library alongside nimbus. Unnecessary duplication.
- Spring Security OAuth2 Resource Server auto-config — too opinionated for custom JwtAuthenticationFilter; would conflict with the manual filter approach.

## R2: RSA Key Loading from Environment Variables

**Decision**: Load RSA keys from PEM-encoded environment variables (JWT_PRIVATE_KEY, JWT_PUBLIC_KEY) at application startup.
**Rationale**: Constitution VI mandates secrets via environment variables. PEM strings can be injected directly as multi-line env vars or base64-encoded. nimbus-jose-jwt provides `JWK.parseFromPEMEncodedObjects()` for direct parsing.
**Alternatives considered**:
- File-based keystore (JKS/PKCS12) — requires mounting a file into the container and a keystore password. More operational complexity.
- Auto-generated key pair at startup — no key sharing across instances; breaks horizontal scaling.

## R3: Refresh Token Storage Strategy

**Decision**: Generate a 32-byte random value via `SecureRandom`, encode as Base64URL, return to client. Store SHA-256 hash in `refresh_tokens.token_hash` column.
**Rationale**: Refresh tokens are high-entropy random strings (not passwords), so SHA-256 is sufficient (no need for BCrypt). If the database is breached, hashed tokens are useless without the original value. The existing schema already has `token_hash` column.
**Alternatives considered**:
- Store raw token — database compromise leaks usable tokens. Rejected for security.
- Use JWT for refresh tokens too — unnecessarily complex; refresh tokens don't need claims since they're validated via DB lookup anyway.

## R4: openapi-generator-maven-plugin Configuration

**Decision**: Use openapi-generator-maven-plugin 7.x with `spring` generator, `interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`.
**Rationale**: Generates Java interfaces with Spring MVC annotations that controllers implement. `interfaceOnly=true` prevents generating controller implementations (we write those). `useTags=true` groups endpoints into separate interfaces by tag (AuthApi, UserApi, AdminApi).
**Alternatives considered**:
- Hand-written controller interfaces — PROHIBITED by constitution II.
- swagger-codegen — older, less maintained; openapi-generator is the community fork with active development.

**Key plugin configuration**:
```xml
<configOptions>
    <interfaceOnly>true</interfaceOnly>
    <useSpringBoot3>true</useSpringBoot3>
    <useTags>true</useTags>
    <dateLibrary>java8</dateLibrary>
    <openApiNullable>false</openApiNullable>
    <skipDefaultInterface>true</skipDefaultInterface>
    <generatedConstructorWithRequiredArgs>false</generatedConstructorWithRequiredArgs>
</configOptions>
<apiPackage>ru.zahaand.lifesync.api</apiPackage>
<modelPackage>ru.zahaand.lifesync.api.model</modelPackage>
```

## R5: SpringDoc OpenAPI 2.x Integration

**Decision**: springdoc-openapi-starter-webmvc-ui 2.8.x in lifesync-web module.
**Rationale**: SpringDoc 2.x is the maintained OpenAPI documentation solution for Spring Boot 3.x. Provides Swagger UI at `/swagger-ui.html` and JSON spec at `/v3/api-docs`. Can overlay annotations on top of the static YAML.
**Alternatives considered**:
- springfox — unmaintained, does not support Spring Boot 3.x.
- Serving static YAML manually — loses auto-discovered schema annotations and Swagger UI integration.

**Configuration**: Define `@SecurityScheme(type = HTTP, scheme = "bearer", bearerFormat = "JWT")` in OpenApiConfig to enable the Authorize button.

## R6: Login Identifier Heuristic

**Decision**: If the login identifier contains `@`, look up by email; otherwise look up by username (case-insensitive).
**Rationale**: Username validation (FR-003) restricts to `[a-z0-9_-]`, which excludes `@`. Email always contains `@`. The heuristic is deterministic with zero ambiguity given these constraints.
**Alternatives considered**:
- Separate login fields (email field + username field) — rejected per clarification: single identifier field.
- Try both lookups — unnecessary overhead and complicates error handling.

## R7: Banned User Check Strategy

**Decision**: Check `enabled` status in JwtAuthenticationFilter by loading user from DB on every authenticated request.
**Rationale**: Ensures banned users are blocked immediately (within current request), not just at next token refresh. The max delay is bounded by request frequency, not by access token TTL.
**Performance note**: This adds one DB query per authenticated request. Acceptable for current scale. Can be optimized with Caffeine cache (evict on ban event) in a future sprint.
**Alternatives considered**:
- Check only at login/refresh — leaves a 15-minute window where banned users remain active. Rejected per SC-006 ("immediately unable").
- Token blacklist — adds cache infrastructure (Redis) for a small number of bans. Over-engineered for current scale.

## R8: Password Validation Rules

**Decision**: Minimum 8 characters, at least 1 uppercase, 1 lowercase, 1 digit. Validated in RegisterUserUseCase.
**Rationale**: From spec assumptions. Standard password policy suitable for a portfolio project. No special character requirement to avoid UX friction.
**Regex**: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$`

## R9: Pagination Strategy for Admin User Listing

**Decision**: Page-based pagination with `page` (0-indexed) and `size` (default 20, max 100) query parameters. Response includes `content`, `totalElements`, `totalPages`, `page`, `size`.
**Rationale**: Standard Spring-style pagination contract. Simple for admin UIs to consume. Max size of 100 prevents accidental full-table fetches.
**Alternatives considered**:
- Cursor-based pagination — better for large datasets but adds complexity. Admin user listing at current scale does not justify it.
- Offset/limit — functionally equivalent but page/size is more idiomatic in Spring ecosystems.
