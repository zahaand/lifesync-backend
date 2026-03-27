# Research: Foundation Infrastructure

**Date**: 2026-03-27
**Feature**: 001-foundation-infra

## R1: Maven Multi-Module without spring-boot-starter-parent as parent

**Decision**: Use `<dependencyManagement>` with Spring Boot BOM import instead of
`<parent>` inheritance.

**Rationale**: The constitution requires the parent POM slot for the project's own
aggregator. Importing the BOM via `<dependencyManagement>` with `type: pom` and
`scope: import` gives identical version management without inheriting plugin
configuration. The spring-boot-maven-plugin must be explicitly configured in the
lifesync-app module with version specified.

**Alternatives considered**:
- `<parent>` to spring-boot-starter-parent: rejected — blocks multi-module
  aggregator pattern required by constitution.
- Flat module structure (no parent): rejected — cannot enforce shared properties
  or dependency versions centrally.

## R2: Liquibase SQL format vs XML format

**Decision**: Use Liquibase XML format (`.xml` files with explicit `<changeSet>`
and `<rollback>` blocks).

**Rationale**: XML format provides native support for `<rollback>` blocks,
`<include>` directives in master changelog, and is the standard Liquibase idiom.
Constitution prohibition on XML comments applies to content inside `<changeSet>`
blocks, not to the XML structure itself. Portfolio readability (principle VII)
is served by explicit, self-documenting XML over terse SQL comments.

**Alternatives considered**:
- SQL format: rejected — `--rollback` comment syntax is less explicit than
  `<rollback>` block; no native `<include>` support for master changelog.
- YAML format: rejected — not idiomatic for Liquibase migrations.

## R3: Liquibase master changelog inclusion strategy

**Decision**: Use XML master changelog (`db.changelog-master.xml`) that includes
individual XML files via `<include>` directives.

**Rationale**: All migration files use Liquibase XML format with `<changeSet>`
and `<rollback>` blocks. The master changelog includes each file via `<include>`
directives. Each included file is a standalone XML migration. The
"no XML comments" rule applies inside `<changeSet>` blocks, not to the XML
structure itself.

**Alternatives considered**:
- Single monolithic changelog: rejected — violates domain directory structure
  requirement (FR-011).
- SQL-only with `includeAll`: viable but `include` with explicit ordering gives
  deterministic migration order across environments.

## R4: UUID primary key strategy

**Decision**: Use `UUID` column type with `gen_random_uuid()` default in
PostgreSQL 16.

**Rationale**: PostgreSQL 16 has native `gen_random_uuid()` (no extension needed).
UUIDs generated at the database level ensure consistency regardless of which
module inserts data. The `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` pattern
is idiomatic for modern PostgreSQL.

**Alternatives considered**:
- Application-side UUID generation: rejected — adds unnecessary coupling for the
  foundation sprint; database default is simpler.
- `uuid-ossp` extension: rejected — `gen_random_uuid()` is built-in since PG 13.

## R5: Docker Compose Kafka image choice

**Decision**: Use Confluent Platform images (`confluentinc/cp-zookeeper`,
`confluentinc/cp-kafka`).

**Rationale**: Confluent images are the most widely documented and stable for
local development. They provide consistent behavior with Spring Kafka defaults.
The user's spec explicitly calls for Zookeeper-based setup.

**Alternatives considered**:
- `bitnami/kafka`: viable but less documentation alignment with Spring Kafka.
- KRaft mode (no Zookeeper): rejected — spec explicitly includes Zookeeper.

## R6: Spring Security in web module with no auth this sprint

**Decision**: Include `spring-boot-starter-security` in lifesync-web POM but
configure a permissive security filter chain in lifesync-app that permits all
requests. Authentication (JWT RS256) is Sprint 2 scope.

**Rationale**: The module dependency allocation (from user input) places security
in lifesync-web. Including it now ensures the module compiles and the dependency
boundary is established. The permissive filter prevents 401 on /actuator/health.

**Alternatives considered**:
- Exclude security entirely: rejected — user input explicitly allocates it to
  lifesync-web; adding it later would change the module's POM mid-project.
- Auto-configure only: rejected — Spring Security defaults to deny-all, which
  would block the health endpoint.
