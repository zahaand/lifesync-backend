# Feature Specification: Authentication and User Management

**Feature Branch**: `002-auth-user-mgmt`
**Created**: 2026-03-27
**Status**: Draft
**Input**: User description: "Sprint 2 — Authentication and User Management for LifeSync Backend"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - User Registration and Login (Priority: P1)

A new user visits the application, creates an account by providing their email, username, and password, then logs in. The system returns an access token and a refresh token. The user can now make authenticated requests using the access token. When the access token expires, the user exchanges the refresh token for a new access token without re-entering credentials.

**Why this priority**: Without registration and login, no other functionality can be used. This is the foundation of all authenticated interactions in the system.

**Independent Test**: Can be fully tested by registering a new account, logging in, receiving tokens, and using the access token to access a protected resource. Delivers the core ability to identify users.

**Acceptance Scenarios**:

1. **Given** a visitor with a valid email, username, and password, **When** they submit the registration form, **Then** the system creates the account and returns a success confirmation.
2. **Given** a registered user with valid credentials (email or username + password), **When** they log in, **Then** the system returns an access token (valid for 15 minutes) and a refresh token (valid for 7 days).
3. **Given** a user with an expired access token and a valid refresh token, **When** they request a token refresh, **Then** the system issues a new access token and a new refresh token, and revokes the old refresh token.
4. **Given** a user with a valid refresh token, **When** they log out, **Then** the refresh token is revoked and can no longer be used.
5. **Given** a visitor attempting to register with an email or username that already exists, **When** they submit the registration form, **Then** the system rejects the request with a clear error message.
6. **Given** a visitor providing an invalid email format or a password that does not meet strength requirements, **When** they submit the registration form, **Then** the system rejects the request with specific validation errors.
7. **Given** a user attempting to log in with incorrect credentials, **When** they submit the login form, **Then** the system rejects the request without revealing which field (email or password) was wrong.

---

### User Story 2 - User Profile Management (Priority: P2)

An authenticated user views their own profile, which combines account information (email, username) and profile details (display name, timezone, locale). The user updates their display name and timezone. The user also connects their Telegram account by providing a Telegram chat ID to enable future notifications. When the user no longer wants an account, they delete it — the system performs a soft delete preserving data for audit purposes.

**Why this priority**: Profile management is the next logical step after authentication. Users need to personalize their experience and manage their account lifecycle. Telegram integration enables a future notifications channel.

**Independent Test**: Can be fully tested by logging in, viewing profile, updating fields, connecting Telegram, and deleting the account. Delivers self-service account management.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they request their profile, **Then** the system returns their account data (email, username) combined with profile data (display name, timezone, locale).
2. **Given** an authenticated user, **When** they update their display name, timezone, or locale, **Then** the system persists the changes and confirms the update.
3. **Given** an authenticated user, **When** they provide a Telegram chat ID, **Then** the system stores the association for future notification delivery.
4. **Given** an authenticated user, **When** they delete their account, **Then** the system performs a soft delete (marks the account as deleted with a timestamp) and the user can no longer log in.
5. **Given** an authenticated user updating their profile with invalid data (e.g., unsupported timezone), **When** they submit the update, **Then** the system rejects the request with specific validation errors.

---

### User Story 3 - Admin User Management (Priority: P3)

An administrator views a list of all users in the system, inspects a specific user's details, and bans a user who has violated the terms of service. Banning disables the user's account, preventing them from logging in or using any authenticated functionality.

**Why this priority**: Admin capabilities are necessary for platform governance but are secondary to core user-facing functionality. Only relevant once there are users in the system.

**Independent Test**: Can be fully tested by logging in as an admin, listing users, viewing a specific user, and banning them. Delivers platform governance capability.

**Acceptance Scenarios**:

1. **Given** an authenticated admin, **When** they request the list of all users, **Then** the system returns a paginated list of users (including soft-deleted) with their account details and status, supporting optional filtering by status (active, banned, deleted) and search by email or username.
2. **Given** an authenticated admin, **When** they request a specific user by ID, **Then** the system returns that user's full account and profile details.
3. **Given** an authenticated admin, **When** they ban a user, **Then** the system disables the user's account, revokes all of the user's refresh tokens, and the banned user can no longer log in or refresh tokens. Active access tokens expire naturally (max 15-minute window).
4. **Given** a regular (non-admin) user, **When** they attempt to access admin endpoints, **Then** the system denies access with a forbidden error.
5. **Given** an authenticated admin, **When** they attempt to ban a user that does not exist, **Then** the system returns a not-found error.

---

### User Story 4 - Interactive API Documentation (Priority: P4)

A developer or tester opens the Swagger UI documentation page, browses all available endpoints, configures a JWT bearer token via the Authorize button, and tests endpoints directly from the browser. All 11 endpoints (4 auth + 4 user + 3 admin) are documented with request/response examples.

**Why this priority**: API documentation enables external testing, developer onboarding, and stakeholder demos. Important but not part of core runtime functionality.

**Independent Test**: Can be fully tested by opening Swagger UI in a browser, verifying all endpoints are listed, using the Authorize button to set a token, and executing test requests. Delivers self-service API exploration.

**Acceptance Scenarios**:

1. **Given** a developer accessing the Swagger UI URL, **When** the page loads, **Then** all 11 REST endpoints are visible with descriptions, request schemas, and response schemas.
2. **Given** a developer on the Swagger UI page, **When** they click the Authorize button and enter a valid JWT token, **Then** subsequent requests include the token and authenticated endpoints work correctly.
3. **Given** a developer on the Swagger UI page, **When** they execute a public endpoint (e.g., register, login), **Then** they receive the expected response without needing to authorize first.

---

### Edge Cases

- What happens when a user tries to refresh with an already-revoked refresh token? The system rejects the request with an authentication error.
- What happens when a user tries to log in after being banned by an admin? The system rejects the login with an error indicating the account is disabled.
- What happens when a soft-deleted user tries to log in? The system rejects the login as if the account does not exist.
- What happens when a user tries to register with a username that differs only in letter casing from an existing username? The system treats usernames as case-insensitive for uniqueness validation.
- What happens when multiple refresh tokens exist for the same user (e.g., logged in from multiple devices)? Each refresh token operates independently — revoking one does not affect others.
- What happens when an admin tries to ban another admin? The system allows it — admin banning follows the same rules regardless of the target user's role.
- What happens when two refresh requests arrive simultaneously with the same refresh token? The first request succeeds (rotates the token), and the second request fails with 401 (token already revoked). This is handled naturally by the rotation logic.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow new users to register with email, username, and password.
- **FR-002**: System MUST validate that email addresses are well-formed and unique across all accounts.
- **FR-003**: System MUST validate that usernames are unique (case-insensitive comparison), 3–32 characters long, and contain only letters (a-z), digits (0-9), underscores, and hyphens.
- **FR-004**: System MUST store passwords as BCrypt hashes with a cost factor of 12.
- **FR-005**: System MUST accept either email or username as the login identifier (single field) alongside the password, and issue a short-lived access token (15-minute validity) and a long-lived refresh token (7-day validity) upon successful login.
- **FR-006**: System MUST use RS256 (RSA-SHA256) asymmetric signing for access tokens.
- **FR-007**: System MUST rotate refresh tokens on each refresh request — issuing a new access token and a new refresh token while revoking the old refresh token (refresh token rotation).
- **FR-008**: System MUST allow users to revoke a refresh token (logout), making it permanently unusable.
- **FR-009**: System MUST persist refresh tokens in a dedicated store and validate them on each refresh request.
- **FR-010**: System MUST assign the USER role by default to all newly registered users.
- **FR-011**: System MUST support two roles: USER and ADMIN.
- **FR-012**: System MUST restrict admin endpoints to users with the ADMIN role only.
- **FR-013**: System MUST allow authenticated users to view their own combined account and profile data.
- **FR-014**: System MUST allow authenticated users to update their display name, timezone, and locale.
- **FR-015**: System MUST allow authenticated users to store a Telegram chat ID for future notification integration.
- **FR-016**: System MUST allow authenticated users to soft-delete their own account by setting a deletion timestamp.
- **FR-017**: System MUST prevent soft-deleted users from logging in.
- **FR-018**: System MUST allow admins to list all users in the system (including soft-deleted users), with optional filtering by status (active, banned, deleted) and search by email or username.
- **FR-019**: System MUST allow admins to view any individual user's details by ID.
- **FR-020**: System MUST allow admins to ban a user by disabling their account and revoking all of the user's active refresh tokens. Already-issued access tokens are not blacklisted and expire naturally within their 15-minute validity window.
- **FR-021**: System MUST prevent banned (disabled) users from logging in.
- **FR-022**: System MUST expose all endpoints through an interactive API documentation interface with bearer token authorization support.
- **FR-023**: All authentication endpoints (register, login, refresh, logout) MUST be publicly accessible without requiring an existing token.
- **FR-024**: System MUST return appropriate error messages for validation failures without exposing internal details.
- **FR-025**: System MUST not reveal whether an email or username is already taken during failed login attempts (to prevent user enumeration).
- **FR-026**: Banned users (enabled=false) attempting to authenticate MUST receive HTTP 403 with message "Account is disabled". Revealing account existence at this point is acceptable — ban is an admin action, not a security boundary.

### Key Entities

- **User**: Represents a registered account. Key attributes: email (unique), username (unique, case-insensitive), password hash, role (USER or ADMIN), enabled status (for banning), soft-delete timestamp. A user has one profile.
- **User Profile**: Extended user information for personalization. Key attributes: display name, timezone, locale, Telegram chat ID. Belongs to exactly one user.
- **Refresh Token**: A long-lived credential for obtaining new access tokens. Key attributes: token value, associated user, expiry time, revocation status. A user can have multiple active refresh tokens (multi-device support).
- **Access Token**: A short-lived credential included in authenticated requests. Key attributes: user identity, role, expiry time. Not persisted — validated by signature verification.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can complete the full registration-to-login flow (register, receive confirmation, log in, receive tokens) in under 60 seconds.
- **SC-002**: Token refresh completes in under 1 second from the user's perspective, ensuring seamless session continuity.
- **SC-003**: 100% of authenticated endpoints reject requests without a valid token, returning appropriate error responses.
- **SC-004**: 100% of admin endpoints reject requests from non-admin users, returning a forbidden response.
- **SC-005**: All 11 REST endpoints are discoverable and testable through the interactive API documentation interface.
- **SC-006**: Banned users are immediately unable to log in or refresh tokens after being banned by an admin.
- **SC-007**: Soft-deleted users are immediately unable to log in after account deletion.
- **SC-008**: A user logged in from multiple devices can revoke one session without affecting others.

## Clarifications

### Session 2026-03-27

- Q: Should the refresh endpoint rotate the refresh token (issue new refresh token + revoke old) or only issue a new access token? → A: Rotate — issue new access token + new refresh token; revoke the old refresh token.
- Q: What credential does the user provide to log in — email, username, or either? → A: Either email or username (single field) + password.
- Q: When an admin bans a user, what happens to existing tokens? → A: Revoke all refresh tokens; active access tokens expire naturally (max 15 min window).
- Q: What are the allowed characters and length limits for usernames? → A: 3–32 characters; a-z, 0-9, underscore, hyphen.
- Q: Should admin user list include soft-deleted users, and support filtering/searching? → A: All users (including soft-deleted) with status filter (active/banned/deleted) and search by email/username.

## Assumptions

- Users have a stable internet connection and access to a modern web browser or API client for interacting with the REST API.
- The system is not expected to handle password reset or email verification flows — these are explicitly out of scope.
- OAuth2 and social login integrations are not needed — all authentication is handled via email/password credentials.
- Rate limiting is out of scope for this sprint — brute-force protection for login will be addressed separately if needed.
- The ADMIN role is assigned manually (e.g., via direct database update or seed data) — there is no self-service admin registration.
- Timezone values follow standard IANA timezone identifiers (e.g., "Europe/Moscow", "America/New_York").
- Locale values follow BCP 47 language tags (e.g., "en-US", "ru-RU").
- The Telegram chat ID is provided by the user (obtained from the Telegram bot interaction) — the system stores it but does not validate it against Telegram's API.
- Pagination defaults for admin user listing: page-based with a default page size of 20 items.
- Password strength requirements: minimum 8 characters with at least one uppercase letter, one lowercase letter, and one digit.
- Registration intentionally returns 409 on duplicate email/username — this is standard UX (user must know the field is taken). Login intentionally does not reveal whether email/username exists — returns generic 401 in both cases. This asymmetry is by design.
- JwtAuthenticationFilter is placed in lifesync-web (HTTP layer concern). SecurityConfig (filter chain wiring) is placed in lifesync-app. This respects the constitution: web handles HTTP concerns, app handles top-level wiring.
- Expired and revoked refresh tokens are not cleaned up from the database in this sprint. Cleanup via scheduled job is deferred to Sprint 7 (Observability).
