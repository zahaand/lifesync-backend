# Quickstart: Authentication and User Management

**Feature**: 002-auth-user-mgmt | **Date**: 2026-03-27

## Prerequisites

1. Docker & Docker Compose installed
2. Java 21 (Temurin) installed
3. Maven 3.9+ (or use `./mvnw`)

## Environment Setup

```bash
# Start PostgreSQL and Kafka
docker compose up -d

# Copy and configure environment
cp .env.example .env
```

Add these new environment variables to `.env` for JWT:

```bash
# Generate RSA key pair for JWT RS256
openssl genrsa -out /tmp/jwt-private.pem 2048
openssl rsa -in /tmp/jwt-private.pem -pubout -out /tmp/jwt-public.pem

# Add to .env (or export directly)
JWT_PRIVATE_KEY=$(cat /tmp/jwt-private.pem)
JWT_PUBLIC_KEY=$(cat /tmp/jwt-public.pem)
JWT_ACCESS_TOKEN_EXPIRY=900
JWT_REFRESH_TOKEN_EXPIRY=604800
```

## Build & Run

```bash
# Full build (runs jOOQ codegen, openapi-generator, compile, test)
./mvnw clean verify

# Run application
./mvnw spring-boot:run -pl lifesync-app
```

## Verify

### Swagger UI

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

All 11 endpoints should be visible, grouped by Auth / User / Admin tags.

### Quick Smoke Test

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","username":"testuser","password":"SecurePass1"}'

# 2. Login
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"testuser","password":"SecurePass1"}'
# → save accessToken and refreshToken from response

# 3. Get profile (replace TOKEN)
curl -s http://localhost:8080/api/v1/users/me \
  -H 'Authorization: Bearer TOKEN'

# 4. Refresh token (replace REFRESH_TOKEN)
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"REFRESH_TOKEN"}'

# 5. Logout (replace REFRESH_TOKEN)
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

## Testing

```bash
# Unit tests only
./mvnw test

# Integration tests (requires Docker for Testcontainers)
./mvnw verify -pl lifesync-app

# Coverage report
./mvnw verify
# Report at: lifesync-domain/target/site/jacoco/index.html
#            lifesync-application/target/site/jacoco/index.html
```

## Key Files

| Artifact | Path |
|----------|------|
| OpenAPI YAML | `lifesync-api-spec/src/main/resources/openapi/lifesync-api.yaml` |
| SecurityConfig | `lifesync-app/src/main/java/.../config/SecurityConfig.java` |
| JWT Filter | `lifesync-app/src/main/java/.../config/JwtAuthenticationFilter.java` |
| Auth Controller | `lifesync-web/src/main/java/.../user/AuthController.java` |
| User migrations | `lifesync-infrastructure/src/main/resources/db/changelog/user/` |
| application.yml | `lifesync-app/src/main/resources/application.yml` |
