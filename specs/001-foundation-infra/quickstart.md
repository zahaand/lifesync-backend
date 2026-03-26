# Quickstart: Foundation Infrastructure

**Date**: 2026-03-27
**Feature**: 001-foundation-infra

## Prerequisites

- Java 21 (verify: `java --version`)
- Maven 3.9+ (or use `./mvnw` wrapper)
- Docker and Docker Compose (verify: `docker compose version`)

## 1. Clone and enter the project

```bash
git clone <repository-url>
cd lifesync-backend
```

## 2. Set up environment variables

```bash
cp .env.example .env
# Edit .env if you want to change default credentials (optional for local dev)
```

## 3. Start backing services

```bash
docker compose up -d
```

This starts:
- PostgreSQL 16 on port 5432
- Zookeeper on port 2181
- Kafka broker on port 9092

Verify services are healthy:

```bash
docker compose ps
```

## 4. Build the project

```bash
./mvnw clean verify
```

This compiles all 6 modules in dependency order and runs tests.
Expected result: BUILD SUCCESS.

## 5. Run the application

```bash
./mvnw -pl lifesync-app spring-boot:run
```

On first startup, Liquibase automatically creates all 11 database tables.

## 6. Verify health endpoint

```bash
curl http://localhost:8080/actuator/health
```

Expected response: `{"status":"UP"}` with HTTP 200.

## 7. Stop services

```bash
# Stop the application: Ctrl+C
# Stop backing services:
docker compose down
# To also remove database volume:
docker compose down -v
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 5432 in use | Stop local PostgreSQL or change DB_PORT in .env |
| Port 9092 in use | Stop local Kafka or change KAFKA_PORT in .env |
| Build fails on module not found | Run `./mvnw clean install` from root first |
| Liquibase fails on startup | Check DB_HOST/DB_PORT/DB_NAME in .env match docker-compose |
