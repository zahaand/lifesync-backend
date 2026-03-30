# Data Model: Authentication and User Management

**Feature**: 002-auth-user-mgmt | **Date**: 2026-03-27

## Entity Relationship Overview

```
┌─────────────┐       1:1       ┌──────────────────┐
│    User      │───────────────▶│   UserProfile     │
│              │                │                    │
│ id (PK)      │                │ id (PK)            │
│ email        │                │ user_id (FK, UQ)   │
│ username     │                │ display_name       │
│ password_hash│                │ timezone           │
│ role         │◀── V12 NEW     │ locale             │
│ enabled      │                │ telegram_chat_id   │◀── V13 NEW
│ created_at   │                │ created_at         │
│ updated_at   │                │ updated_at         │
│ deleted_at   │                └──────────────────┘
└─────────────┘
       │
       │ 1:N
       ▼
┌──────────────────┐
│  RefreshToken     │
│                    │
│ id (PK)            │
│ user_id (FK)       │
│ token_hash (UQ)    │
│ expires_at         │
│ revoked            │
└──────────────────┘
```

## Entities

### User (table: `users`)

Existing table (V1). Modified by V12 to add `role` column.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default gen_random_uuid() | |
| email | varchar(255) | NOT NULL, UNIQUE | |
| username | varchar(100) | NOT NULL, UNIQUE | Case-insensitive uniqueness (validated in application layer) |
| password_hash | varchar(255) | NOT NULL | BCrypt cost=12 |
| role | varchar(20) | NOT NULL, default 'USER' | **NEW (V12)** — values: USER, ADMIN |
| enabled | boolean | NOT NULL, default true | false = banned |
| created_at | timestamptz | NOT NULL, default now() | |
| updated_at | timestamptz | NOT NULL, default now() | |
| deleted_at | timestamptz | nullable | Soft delete timestamp |

**Indexes** (existing): uq_users_email, uq_users_username

**State transitions**:
- Created → Active (enabled=true, deleted_at=null)
- Active → Banned (enabled=false, set by admin)
- Active → Deleted (deleted_at set, set by user)
- Banned → remains banned (no unban in this sprint)

**Validation rules**:
- email: well-formed email, unique
- username: 3–32 chars, `[a-z0-9_-]` only, unique (case-insensitive)
- password: min 8 chars, 1 uppercase, 1 lowercase, 1 digit (validated pre-hash)

### UserProfile (table: `user_profiles`)

Existing table (V2). Modified by V13 to add `telegram_chat_id` column.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default gen_random_uuid() | |
| user_id | uuid | NOT NULL, UNIQUE, FK → users.id ON DELETE CASCADE | |
| display_name | varchar(150) | nullable | |
| timezone | varchar(50) | NOT NULL, default 'UTC' | IANA timezone identifier |
| locale | varchar(10) | NOT NULL, default 'en' | BCP 47 language tag |
| telegram_chat_id | varchar(50) | nullable | **NEW (V13)** — Telegram chat ID for notifications |
| created_at | timestamptz | NOT NULL, default now() | |
| updated_at | timestamptz | NOT NULL, default now() | |

**Indexes** (existing): uq_user_profiles_user_id, fk_user_profiles_user

**Validation rules**:
- timezone: must be valid IANA timezone (validated in application layer)
- locale: must be valid BCP 47 tag (validated in application layer)
- telegram_chat_id: string, no server-side Telegram API validation

### RefreshToken (table: `refresh_tokens`)

Existing table (V3). No schema changes needed.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default gen_random_uuid() | |
| user_id | uuid | NOT NULL, FK → users.id ON DELETE CASCADE | |
| token_hash | varchar(255) | NOT NULL, UNIQUE | SHA-256 hash of raw token |
| expires_at | timestamptz | NOT NULL | 7 days from creation |
| revoked | boolean | NOT NULL, default false | Set true on logout/rotation/ban |

**Indexes** (existing): uq_refresh_tokens_token_hash, fk_refresh_tokens_user

**Lifecycle**:
- Created on login (raw token returned to client, hash stored)
- Rotated on refresh (old revoked, new created)
- Revoked on logout (single token)
- Bulk revoked on ban (all tokens for user)
- Expired tokens: not actively cleaned (can add scheduled cleanup later)

### AccessToken (not persisted)

Stateless JWT, validated by RS256 signature verification only.

| Claim | Type | Description |
|-------|------|-------------|
| sub | string (UUID) | User ID |
| email | string | User email |
| role | string | USER or ADMIN |
| iat | number | Issued-at timestamp |
| exp | number | Expiration (iat + 15 minutes) |

## New Migrations

### V12__add_role_to_users.xml

```xml
<changeSet id="V12-add-role-to-users" author="zahaand">
    <addColumn tableName="users">
        <column name="role" type="varchar(20)" defaultValue="USER">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <rollback>
        <dropColumn tableName="users" columnName="role"/>
    </rollback>
</changeSet>
```

### V13__add_telegram_to_profiles.xml

```xml
<changeSet id="V13-add-telegram-to-profiles" author="zahaand">
    <addColumn tableName="user_profiles">
        <column name="telegram_chat_id" type="varchar(50)"/>
    </addColumn>
    <rollback>
        <dropColumn tableName="user_profiles" columnName="telegram_chat_id"/>
    </rollback>
</changeSet>
```

## Domain Model (Java)

### Role (enum)

```java
package ru.zahaand.lifesync.domain.user;

public enum Role {
    USER,
    ADMIN
}
```

### UserId (value object)

```java
package ru.zahaand.lifesync.domain.user;

import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId must not be null");
        }
    }
}
```

### User (entity)

Key fields: id (UserId), email, username, passwordHash, role (Role), enabled, createdAt, updatedAt, deletedAt.
Encapsulates: ban(), softDelete(), isActive(), isBanned(), isDeleted().

### UserProfile (value object)

Key fields: displayName, timezone, locale, telegramChatId.
Immutable. New instances created on update.

## Port Interfaces

### UserRepository

```
findById(UserId) → Optional<User>
findByEmail(String) → Optional<User>
findByUsername(String) → Optional<User>
existsByEmail(String) → boolean
existsByUsername(String) → boolean
save(User) → User
update(User) → User
findAll(status filter, search query, page, size) → Page<User>
```

### RefreshTokenRepository

```
save(userId, tokenHash, expiresAt) → void
findByTokenHash(String) → Optional<RefreshTokenRecord>
revokeByTokenHash(String) → void
revokeAllByUserId(UserId) → void
```

### PasswordEncoder

```
encode(String rawPassword) → String hash
matches(String rawPassword, String hash) → boolean
```

### TokenProvider

```
generateAccessToken(User) → String jwt
generateRefreshToken() → TokenPair(rawToken, hash)
validateAccessToken(String jwt) → TokenClaims
```
