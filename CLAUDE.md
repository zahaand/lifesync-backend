# lifesync-backend Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-31

## Active Technologies
- Java 21 LTS + Spring Boot 3.5.x, Spring Security 6.x, jOOQ 3.19, Liquibase 4.x, nimbus-jose-jwt 9.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0 (002-auth-user-mgmt)
- PostgreSQL 16 (existing via Docker Compose) (002-auth-user-mgmt)
- Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Liquibase 4.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0 (004-habits-core)
- PostgreSQL 16 (via Docker Compose) (004-habits-core)
- Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0 (006-goals-feature)
- PostgreSQL 16 (existing tables: goals, goal_milestones, goal_habits) (006-goals-feature)
- Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, commons-lang3 3.17.0 (007-reminders-notifications)
- PostgreSQL 16 (existing tables + 2 new: `sent_reminders`, `goal_sent_milestones`) (007-reminders-notifications)

- Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Liquibase 4.x, (001-foundation-infra)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 21 LTS

## Code Style

Java 21 LTS: Follow standard conventions

## Recent Changes
- 007-reminders-notifications: Added Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, commons-lang3 3.17.0
- 006-goals-feature: Added Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Spring Kafka 3.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0
- 004-habits-core: Added Java 21 LTS + Spring Boot 3.5.x, jOOQ 3.19, Liquibase 4.x, SpringDoc OpenAPI 2.x, commons-lang3 3.17.0


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
