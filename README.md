🇷🇺 Описание на русском ниже &nbsp;/&nbsp; 🇬🇧 Russian description below

---

# LifeSync Backend `v1.0.0`

B2C habit and goal tracking platform REST API.
Built with Java 21 and Spring Boot 3.5 using Hexagonal Architecture (Ports & Adapters).

32 REST endpoints | 6 Kafka consumers | 19 Liquibase migrations | 251 tests

## Methodology

Built using **Spec-Driven Development (SDD)** via [Spec Kit](https://github.com/speckit/speckit) and [Claude Code](https://docs.anthropic.com/en/docs/claude-code).

Each feature goes through a structured SDD cycle:

1. **Specify** — natural language feature spec with user stories and acceptance criteria
2. **Plan** — architecture plan with data model, module layout, and constitution compliance check
3. **Tasks** — dependency-ordered task list with phase grouping
4. **Implement** — code generation following the task list, one phase at a time
5. **Verify** — checklist validation, build verification, and code review

The project was developed over 7 sprints, each following this full cycle.

## Architecture

The application follows **Hexagonal Architecture** (Ports & Adapters) with strict inward dependency direction.

```
infrastructure  -->  application  -->  domain
       web      -->  application  -->  domain
       app      (Spring Boot config, wires everything together)
   api-spec     (OpenAPI YAML + generated interfaces)
```

### Maven Modules

| Module | Responsibility |
|--------|---------------|
| `lifesync-api-spec` | OpenAPI 3.1 YAML specification, generated controller interfaces |
| `lifesync-domain` | Pure Java domain model — entities, value objects, ports (interfaces), exceptions. No framework dependencies |
| `lifesync-application` | Use cases (business logic), `@Transactional` boundaries. Depends only on domain |
| `lifesync-infrastructure` | jOOQ repositories, Kafka consumers/publishers, Telegram adapter, Liquibase migrations |
| `lifesync-web` | REST controllers (implement generated interfaces), DTOs, `GlobalExceptionHandler`, JWT filter |
| `lifesync-app` | Spring Boot main class, `SecurityConfig`, `UseCaseConfig`, fat JAR packaging |

### Key Architecture Rules

- **API First** — OpenAPI 3.1 YAML is the single source of truth. Controller interfaces are generated via `openapi-generator-maven-plugin`. Hand-written controller interfaces are prohibited
- **Dependency direction** — infrastructure/web depend on application, application depends on domain. Domain has zero framework imports
- **No Lombok** — all boilerplate is explicit Java (records, explicit constructors, manual getters)
- **Constructor injection only** — no `@Autowired` on fields, all fields `final`
- **Use cases are plain classes** — wired via `@Bean` in `UseCaseConfig`, no `@Service` annotation

## Key Technical Highlights

### API First Development
OpenAPI 3.1 YAML (`lifesync-api.yaml`) is written before any controller code. The `openapi-generator-maven-plugin` generates Java interfaces that controllers implement. Every endpoint includes summary, multi-line description with business rules, named examples, and error documentation per Principle XII.

### Hexagonal Architecture
Six Maven modules with strict layer isolation. Domain module contains pure Java only (no Spring, no jOOQ, no Kafka). Application module contains use cases with `@Transactional` boundaries. Infrastructure adapts external systems to domain ports.

### Event-Driven Architecture
Apache Kafka handles asynchronous processing. When a habit is completed, the `HabitCompletedEvent` triggers streak recalculation, analytics update, Telegram notification, and goal progress recalculation — all via independent Kafka consumers.

### Domain Events
`HabitCompletedEvent` and `GoalProgressUpdatedEvent` are published using Spring's `ApplicationEventPublisher`. `@TransactionalEventListener(phase = AFTER_COMMIT)` in `KafkaHabitEventPublisher` and `KafkaGoalEventPublisher` ensures Kafka messages are sent only after the database transaction commits successfully.

### Kafka Consumers
Six independent consumers, each with its own consumer group for parallel processing:

| Consumer | Topic | Group | Purpose |
|----------|-------|-------|---------|
| `StreakCalculatorConsumer` | `habit.log.completed` | `lifesync-streak-calculator` | Recalculates current and longest streak |
| `AnalyticsUpdaterConsumer` | `habit.log.completed` | `lifesync-analytics-updater` | Invalidates analytics cache |
| `TelegramNotificationConsumer` | `habit.log.completed` | `lifesync-telegram-notifier` | Sends milestone notifications (7/14/21/30/60/90 days) |
| `GoalProgressConsumer` | `habit.log.completed` | `lifesync-goal-progress` | Recalculates goal progress for linked habits |
| `GoalAnalyticsConsumer` | `goal.progress.updated` | `lifesync-goal-analytics` | Goal analytics placeholder |
| `GoalNotificationConsumer` | `goal.progress.updated` | `lifesync-goal-notifier` | Sends goal milestone notifications (25/50/75/100%) |

### Idempotency
Every Kafka consumer checks the `processed_events` table before processing. The table uses a composite unique constraint on `(event_id, consumer_group)`, ensuring each event is processed exactly once per consumer even across restarts.

### Dead Letter Queue (DLQ)
Failed messages are retried with exponential backoff (1s, 2s, 4s) via `DefaultErrorHandler` with `ExponentialBackOff`. After 3 retries, messages are routed to a `.DLT` (Dead Letter Topic) automatically for manual inspection.

### JWT RS256 Authentication
Asymmetric RSA key pair authentication: private key signs tokens, public key verifies them. Access tokens expire in 15 minutes, refresh tokens in 7 days. Refresh token rotation — each refresh invalidates the old token and issues a new pair.

### jOOQ Type-Safe SQL
No ORM (no Hibernate, no Spring Data JPA). All database queries are written with jOOQ's type-safe DSL using classes generated directly from the PostgreSQL schema. Explicit SQL provides full control over queries, joins, and aggregations.

### Liquibase Migrations
19 versioned XML migrations organized by domain (`user/`, `habit/`, `goal/`, `notification/`, `system/`). Every changeset includes a rollback block. Column conventions: UUID primary keys with `gen_random_uuid()`, `timestamptz` for all timestamps, `CASCADE` on all foreign keys.

### Scheduler
Spring `@Scheduled` cron job (`HabitReminderScheduler`) runs every minute, querying habits with `reminderTime` matching the current time in each user's configured timezone. Sends reminders via Kafka to the `TelegramNotificationConsumer`.

### Telegram Notifications
Bot API integration for two types of notifications: habit reminders (configurable per-habit reminder time) and milestone achievements (streak milestones at 7/14/21/30/60/90 days, goal progress at 25/50/75/100%). Controlled via `TELEGRAM_ENABLED` flag (defaults to `false`).

### Testcontainers
Integration tests (`*IT.java`) run against real PostgreSQL 16 and Apache Kafka instances via Testcontainers. Shared `BaseIT` class provides `@Container static` instances, JWT helper methods, and common test utilities. 251 total tests: 170 unit + 81 integration.

### JaCoCo Coverage
Code coverage enforced at build time: minimum 80% line coverage on `lifesync-domain` and `lifesync-application` modules. Build fails if coverage drops below threshold.

### Maven Multi-Module
Six modules with strict dependency order: `api-spec` -> `domain` -> `application` -> `infrastructure` -> `web` -> `app`. Dependency versions centralized in parent `dependencyManagement`. JaCoCo configured in `pluginManagement` for reuse.

### Spec-Driven Development
Every sprint follows the full SDD cycle: specify -> plan -> tasks -> implement -> verify. Specifications, plans, data models, and checklists are preserved in the `specs/` directory. A project constitution (`constitution.md`) enforces architectural principles across all sprints.

## Features

- **User registration and authentication** — JWT RS256, access + refresh token pair, token rotation
- **User profile management** — timezone, locale, display name, Telegram chat integration
- **Admin panel** — user list with pagination and search, user details, ban/unban
- **Habit tracking** — CRUD, three frequency modes (DAILY / WEEKLY / CUSTOM), completion logs with notes
- **Streak calculation** — current streak, longest streak, last completed date, timezone-aware
- **Goal tracking** — CRUD with optional deadline, milestone management, habit-goal linking
- **Automatic goal progress** — recalculated via Kafka events when linked habits are completed
- **Habit reminders** — per-habit reminder time, delivered via Telegram, timezone-aware scheduler
- **Habit milestone notifications** — Telegram alerts at 7, 14, 21, 30, 60, 90 day streaks
- **Goal milestone notifications** — Telegram alerts at 25%, 50%, 75%, 100% progress
- **Interactive Swagger UI** — full API documentation with examples, descriptions, and how-to-test instructions

## API Overview

| Group | Endpoints | Auth | Description |
|-------|-----------|------|-------------|
| Auth | 4 | Public | Register, login, refresh token, logout |
| User | 4 | Bearer | Profile CRUD, Telegram connection |
| Admin | 3 | ADMIN role | User list, user details, ban |
| Habit | 9 | Bearer | Habit CRUD, completion, logs, streak |
| Goal | 12 | Bearer | Goal CRUD, milestones, habit links, progress |

**Total: 32 endpoints**

## Technology Stack

| Concern | Technology |
|---------|-----------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.5.x |
| Build | Maven Multi-Module (6 modules) |
| Database | PostgreSQL 16 |
| SQL | jOOQ 3.19 (no Hibernate, no Spring Data JPA) |
| Migrations | Liquibase 4.x |
| Broker | Apache Kafka (Spring Kafka 3.x) |
| Security | Spring Security 6.x + JWT RS256 (nimbus-jose-jwt) |
| API Docs | SpringDoc OpenAPI 2.x + Swagger UI |
| Code Gen | openapi-generator-maven-plugin 7.x |
| Notifications | Telegram Bot API |
| Tests | JUnit 5 + AssertJ + Mockito + Testcontainers |
| Coverage | JaCoCo (>= 80%) |
| Containers | Docker Compose (PostgreSQL + Kafka + Zookeeper) |

## Local Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker and Docker Compose

### 1. Start infrastructure

```bash
cp .env.example .env
# Edit .env with your values
docker compose up -d
```

### 2. Generate RSA keys for JWT

```bash
mkdir -p lifesync-app/src/main/resources/certs
openssl genrsa -out lifesync-app/src/main/resources/certs/private.pem 2048
openssl rsa -in lifesync-app/src/main/resources/certs/private.pem \
  -pubout -out lifesync-app/src/main/resources/certs/public.pem
```

### 3. Configure environment

Set `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` in `.env` with PEM-encoded keys, or use the dev profile which reads from classpath:

```bash
export SPRING_PROFILES_ACTIVE=dev
```

### 4. Run the application

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl lifesync-app
```

The application starts on `http://localhost:8080`.

### 5. Verify

```
LifeSync Backend started successfully
Swagger UI: http://localhost:8080/swagger-ui.html
```

## Running Tests

```bash
mvn clean verify
```

| Module | Unit Tests | Integration Tests |
|--------|-----------|-------------------|
| `lifesync-domain` | 16 | — |
| `lifesync-application` | 116 | — |
| `lifesync-infrastructure` | 37 | — |
| `lifesync-web` | — | 81 |
| `lifesync-app` | 1 | — |
| **Total** | **170** | **81** |

**251 tests total.** Integration tests require Docker (Testcontainers starts PostgreSQL and Kafka automatically).

## API Documentation

After starting the application:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI YAML:** [http://localhost:8080/lifesync-api.yaml](http://localhost:8080/lifesync-api.yaml)

### How to authenticate in Swagger UI

1. `POST /api/v1/auth/register` — create an account
2. `POST /api/v1/auth/login` — copy `accessToken` from the response
3. Click **Authorize** (top right), paste the token
4. All authenticated endpoints will use your token automatically

---

# LifeSync Backend `v1.0.0`

B2C REST API платформа для трекинга привычек и целей.
Построена на Java 21 и Spring Boot 3.5 с использованием гексагональной архитектуры (Ports & Adapters).

32 REST-эндпоинта | 6 Kafka-консьюмеров | 19 Liquibase-миграций | 251 тест

## Методология

Проект создан с использованием **Spec-Driven Development (SDD)** через [Spec Kit](https://github.com/speckit/speckit) и [Claude Code](https://docs.anthropic.com/en/docs/claude-code).

Каждая фича проходит структурированный SDD-цикл:

1. **Specify** — спецификация на естественном языке с user stories и критериями приёмки
2. **Plan** — архитектурный план с моделью данных, структурой модулей и проверкой соответствия конституции
3. **Tasks** — упорядоченный по зависимостям список задач с группировкой по фазам
4. **Implement** — генерация кода по списку задач, одна фаза за раз
5. **Verify** — валидация чеклиста, проверка сборки и код-ревью

Проект разработан за 7 спринтов, каждый из которых прошёл полный цикл.

## Архитектура

Приложение следует **гексагональной архитектуре** (Ports & Adapters) со строгим направлением зависимостей внутрь.

```
infrastructure  -->  application  -->  domain
       web      -->  application  -->  domain
       app      (Spring Boot конфигурация, связывает всё вместе)
   api-spec     (OpenAPI YAML + сгенерированные интерфейсы)
```

### Maven-модули

| Модуль | Ответственность |
|--------|----------------|
| `lifesync-api-spec` | OpenAPI 3.1 YAML спецификация, сгенерированные интерфейсы контроллеров |
| `lifesync-domain` | Чистая Java доменная модель — сущности, value objects, порты (интерфейсы), исключения. Без зависимостей от фреймворков |
| `lifesync-application` | Use cases (бизнес-логика), границы `@Transactional`. Зависит только от domain |
| `lifesync-infrastructure` | jOOQ-репозитории, Kafka-консьюмеры/паблишеры, Telegram-адаптер, Liquibase-миграции |
| `lifesync-web` | REST-контроллеры (реализуют сгенерированные интерфейсы), DTO, `GlobalExceptionHandler`, JWT-фильтр |
| `lifesync-app` | Spring Boot main-класс, `SecurityConfig`, `UseCaseConfig`, упаковка fat JAR |

### Ключевые архитектурные правила

- **API First** — OpenAPI 3.1 YAML является единственным источником истины. Интерфейсы контроллеров генерируются через `openapi-generator-maven-plugin`. Ручное написание интерфейсов запрещено
- **Направление зависимостей** — infrastructure/web зависят от application, application зависит от domain. Domain не содержит импортов фреймворков
- **Без Lombok** — весь бойлерплейт на чистой Java (records, явные конструкторы, ручные геттеры)
- **Только конструкторная инъекция** — никаких `@Autowired` на полях, все поля `final`
- **Use cases — обычные классы** — подключаются через `@Bean` в `UseCaseConfig`, без аннотации `@Service`

## Ключевые технические решения

### API First разработка
OpenAPI 3.1 YAML (`lifesync-api.yaml`) пишется до любого кода контроллеров. Плагин `openapi-generator-maven-plugin` генерирует Java-интерфейсы, которые реализуют контроллеры. Каждый эндпоинт включает summary, многострочное описание с бизнес-правилами, именованные примеры и документацию ошибок.

### Гексагональная архитектура
Шесть Maven-модулей со строгой изоляцией слоёв. Domain-модуль содержит только чистую Java (без Spring, jOOQ, Kafka). Application-модуль содержит use cases с границами `@Transactional`. Infrastructure адаптирует внешние системы к доменным портам.

### Событийная архитектура
Apache Kafka обеспечивает асинхронную обработку. При завершении привычки событие `HabitCompletedEvent` запускает пересчёт стрика, обновление аналитики, Telegram-уведомление и пересчёт прогресса цели — всё через независимые Kafka-консьюмеры.

### Доменные события
`HabitCompletedEvent` и `GoalProgressUpdatedEvent` публикуются через `ApplicationEventPublisher`. `@TransactionalEventListener(phase = AFTER_COMMIT)` в `KafkaHabitEventPublisher` и `KafkaGoalEventPublisher` гарантирует отправку Kafka-сообщений только после успешного коммита транзакции.

### Kafka-консьюмеры
Шесть независимых консьюмеров, каждый со своей consumer group для параллельной обработки:

| Консьюмер | Топик | Группа | Назначение |
|-----------|-------|--------|-----------|
| `StreakCalculatorConsumer` | `habit.log.completed` | `lifesync-streak-calculator` | Пересчёт текущего и максимального стрика |
| `AnalyticsUpdaterConsumer` | `habit.log.completed` | `lifesync-analytics-updater` | Инвалидация кеша аналитики |
| `TelegramNotificationConsumer` | `habit.log.completed` | `lifesync-telegram-notifier` | Отправка уведомлений о вехах (7/14/21/30/60/90 дней) |
| `GoalProgressConsumer` | `habit.log.completed` | `lifesync-goal-progress` | Пересчёт прогресса целей для связанных привычек |
| `GoalAnalyticsConsumer` | `goal.progress.updated` | `lifesync-goal-analytics` | Аналитика целей (заготовка) |
| `GoalNotificationConsumer` | `goal.progress.updated` | `lifesync-goal-notifier` | Уведомления о вехах целей (25/50/75/100%) |

### Идемпотентность
Каждый Kafka-консьюмер проверяет таблицу `processed_events` перед обработкой. Таблица использует составной уникальный индекс `(event_id, consumer_group)`, гарантируя обработку каждого события ровно один раз на консьюмер даже при перезапусках.

### Dead Letter Queue (DLQ)
Неудачные сообщения повторяются с экспоненциальной задержкой (1с, 2с, 4с) через `DefaultErrorHandler` с `ExponentialBackOff`. После 3 попыток сообщения автоматически перенаправляются в `.DLT` (Dead Letter Topic) для ручной проверки.

### JWT RS256 аутентификация
Аутентификация на основе асимметричной пары RSA-ключей: приватный ключ подписывает токены, публичный — верифицирует. Access-токены истекают через 15 минут, refresh-токены — через 7 дней. Ротация refresh-токенов — каждое обновление инвалидирует старый токен и выпускает новую пару.

### jOOQ Type-Safe SQL
Без ORM (без Hibernate, без Spring Data JPA). Все запросы к базе написаны с помощью типобезопасного DSL jOOQ, используя классы, сгенерированные напрямую из схемы PostgreSQL. Явный SQL обеспечивает полный контроль над запросами, джоинами и агрегациями.

### Liquibase-миграции
19 версионированных XML-миграций, организованных по доменам (`user/`, `habit/`, `goal/`, `notification/`, `system/`). Каждый changeset включает блок отката. Соглашения: UUID primary keys с `gen_random_uuid()`, `timestamptz` для всех временных меток, `CASCADE` на всех foreign keys.

### Планировщик
Spring `@Scheduled` cron-задача (`HabitReminderScheduler`) запускается каждую минуту, выбирая привычки с `reminderTime`, совпадающим с текущим временем в настроенном часовом поясе пользователя. Отправляет напоминания через Kafka в `TelegramNotificationConsumer`.

### Telegram-уведомления
Интеграция с Bot API для двух типов уведомлений: напоминания о привычках (настраиваемое время на каждую привычку) и достижения вех (стрик-вехи на 7/14/21/30/60/90 дней, прогресс целей на 25/50/75/100%). Управляется флагом `TELEGRAM_ENABLED` (по умолчанию `false`).

### Testcontainers
Интеграционные тесты (`*IT.java`) работают с реальными экземплярами PostgreSQL 16 и Apache Kafka через Testcontainers. Общий класс `BaseIT` предоставляет `@Container static`-экземпляры, вспомогательные JWT-методы и общие тестовые утилиты. Всего 251 тест: 170 юнит + 81 интеграционный.

### JaCoCo покрытие
Покрытие кода проверяется при сборке: минимум 80% покрытия строк в модулях `lifesync-domain` и `lifesync-application`. Сборка падает при снижении покрытия ниже порога.

### Maven Multi-Module
Шесть модулей со строгим порядком зависимостей: `api-spec` -> `domain` -> `application` -> `infrastructure` -> `web` -> `app`. Версии зависимостей централизованы в родительском `dependencyManagement`. JaCoCo настроен в `pluginManagement` для переиспользования.

### Spec-Driven Development
Каждый спринт следует полному SDD-циклу: specify -> plan -> tasks -> implement -> verify. Спецификации, планы, модели данных и чеклисты сохранены в директории `specs/`. Конституция проекта (`constitution.md`) обеспечивает соблюдение архитектурных принципов во всех спринтах.

## Функциональность

- **Регистрация и аутентификация** — JWT RS256, пара access + refresh токенов, ротация токенов
- **Управление профилем** — часовой пояс, локаль, отображаемое имя, интеграция с Telegram
- **Админ-панель** — список пользователей с пагинацией и поиском, детали пользователя, бан/разбан
- **Трекинг привычек** — CRUD, три режима частоты (DAILY / WEEKLY / CUSTOM), логи выполнения с заметками
- **Расчёт стриков** — текущий стрик, максимальный стрик, дата последнего выполнения, с учётом часового пояса
- **Трекинг целей** — CRUD с опциональным дедлайном, управление вехами, привязка привычек к целям
- **Автоматический прогресс целей** — пересчитывается через Kafka-события при выполнении связанных привычек
- **Напоминания о привычках** — настраиваемое время на привычку, доставка через Telegram, планировщик с учётом часовых поясов
- **Уведомления о вехах привычек** — Telegram-оповещения на стриках 7, 14, 21, 30, 60, 90 дней
- **Уведомления о вехах целей** — Telegram-оповещения при прогрессе 25%, 50%, 75%, 100%
- **Интерактивный Swagger UI** — полная документация API с примерами, описаниями и инструкциями по тестированию

## Обзор API

| Группа | Эндпоинтов | Авторизация | Описание |
|--------|-----------|-------------|----------|
| Auth | 4 | Публичный | Регистрация, логин, обновление токена, логаут |
| User | 4 | Bearer | CRUD профиля, подключение Telegram |
| Admin | 3 | Роль ADMIN | Список пользователей, детали, бан |
| Habit | 9 | Bearer | CRUD привычек, выполнение, логи, стрик |
| Goal | 12 | Bearer | CRUD целей, вехи, привязка привычек, прогресс |

**Итого: 32 эндпоинта**

## Стек технологий

| Область | Технология |
|---------|-----------|
| Язык | Java 21 LTS |
| Фреймворк | Spring Boot 3.5.x |
| Сборка | Maven Multi-Module (6 модулей) |
| База данных | PostgreSQL 16 |
| SQL | jOOQ 3.19 (без Hibernate, без Spring Data JPA) |
| Миграции | Liquibase 4.x |
| Брокер | Apache Kafka (Spring Kafka 3.x) |
| Безопасность | Spring Security 6.x + JWT RS256 (nimbus-jose-jwt) |
| Документация API | SpringDoc OpenAPI 2.x + Swagger UI |
| Кодогенерация | openapi-generator-maven-plugin 7.x |
| Уведомления | Telegram Bot API |
| Тесты | JUnit 5 + AssertJ + Mockito + Testcontainers |
| Покрытие | JaCoCo (>= 80%) |
| Контейнеры | Docker Compose (PostgreSQL + Kafka + Zookeeper) |

## Локальный запуск

### Требования

- Java 21+
- Maven 3.9+
- Docker и Docker Compose

### 1. Запустить инфраструктуру

```bash
cp .env.example .env
# Отредактируйте .env своими значениями
docker compose up -d
```

### 2. Сгенерировать RSA-ключи для JWT

```bash
mkdir -p lifesync-app/src/main/resources/certs
openssl genrsa -out lifesync-app/src/main/resources/certs/private.pem 2048
openssl rsa -in lifesync-app/src/main/resources/certs/private.pem \
  -pubout -out lifesync-app/src/main/resources/certs/public.pem
```

### 3. Настроить окружение

Укажите `JWT_PRIVATE_KEY` и `JWT_PUBLIC_KEY` в `.env` с PEM-ключами, или используйте dev-профиль, который читает из classpath:

```bash
export SPRING_PROFILES_ACTIVE=dev
```

### 4. Запустить приложение

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl lifesync-app
```

Приложение запускается на `http://localhost:8080`.

### 5. Проверить

```
LifeSync Backend started successfully
Swagger UI: http://localhost:8080/swagger-ui.html
```

## Запуск тестов

```bash
mvn clean verify
```

| Модуль | Юнит-тесты | Интеграционные тесты |
|--------|-----------|---------------------|
| `lifesync-domain` | 16 | — |
| `lifesync-application` | 116 | — |
| `lifesync-infrastructure` | 37 | — |
| `lifesync-web` | — | 81 |
| `lifesync-app` | 1 | — |
| **Итого** | **170** | **81** |

**251 тест всего.** Интеграционные тесты требуют Docker (Testcontainers запускает PostgreSQL и Kafka автоматически).

## Документация API

После запуска приложения:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI YAML:** [http://localhost:8080/lifesync-api.yaml](http://localhost:8080/lifesync-api.yaml)

### Как авторизоваться в Swagger UI

1. `POST /api/v1/auth/register` — создайте аккаунт
2. `POST /api/v1/auth/login` — скопируйте `accessToken` из ответа
3. Нажмите **Authorize** (вверху справа), вставьте токен
4. Все авторизованные эндпоинты будут использовать ваш токен автоматически
