package ru.zahaand.lifesync.infrastructure.user;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqUserRepository implements UserRepository {

    private static final Table<?> USERS = DSL.table("users");
    private static final Table<?> USER_PROFILES = DSL.table("user_profiles");

    private static final Field<UUID> U_ID = DSL.field("users.id", UUID.class);
    private static final Field<String> U_EMAIL = DSL.field("users.email", String.class);
    private static final Field<String> U_USERNAME = DSL.field("users.username", String.class);
    private static final Field<String> U_PASSWORD_HASH = DSL.field("users.password_hash", String.class);
    private static final Field<String> U_ROLE = DSL.field("users.role", String.class);
    private static final Field<Boolean> U_ENABLED = DSL.field("users.enabled", Boolean.class);
    private static final Field<OffsetDateTime> U_CREATED_AT = DSL.field("users.created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> U_UPDATED_AT = DSL.field("users.updated_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> U_DELETED_AT = DSL.field("users.deleted_at", OffsetDateTime.class);

    private static final Field<UUID> P_USER_ID = DSL.field("user_profiles.user_id", UUID.class);
    private static final Field<String> P_DISPLAY_NAME = DSL.field("user_profiles.display_name", String.class);
    private static final Field<String> P_TIMEZONE = DSL.field("user_profiles.timezone", String.class);
    private static final Field<String> P_LOCALE = DSL.field("user_profiles.locale", String.class);
    private static final Field<String> P_TELEGRAM_CHAT_ID = DSL.field("user_profiles.telegram_chat_id", String.class);

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(U_ID.eq(P_USER_ID))
                .where(U_ID.eq(id.value()))
                .and(U_DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(U_ID.eq(P_USER_ID))
                .where(U_EMAIL.eq(email))
                .and(U_DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(U_ID.eq(P_USER_ID))
                .where(DSL.lower(U_USERNAME).eq(username.toLowerCase()))
                .and(U_DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public boolean existsByEmail(String email) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USERS)
                        .where(U_EMAIL.eq(email))
                        .and(U_DELETED_AT.isNull())
        );
    }

    @Override
    public boolean existsByUsername(String username) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USERS)
                        .where(DSL.lower(U_USERNAME).eq(username.toLowerCase()))
                        .and(U_DELETED_AT.isNull())
        );
    }

    @Override
    public User save(User user) {
        UUID userId = user.getId().value();
        OffsetDateTime now = user.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(USERS)
                .set(U_ID, userId)
                .set(U_EMAIL, user.getEmail())
                .set(U_USERNAME, user.getUsername())
                .set(U_PASSWORD_HASH, user.getPasswordHash())
                .set(U_ROLE, user.getRole().name())
                .set(U_ENABLED, user.isEnabled())
                .set(U_CREATED_AT, now)
                .set(U_UPDATED_AT, now)
                .execute();

        UserProfile profile = user.getProfile();
        dsl.insertInto(USER_PROFILES)
                .set(DSL.field("user_profiles.id", UUID.class), UUID.randomUUID())
                .set(P_USER_ID, userId)
                .set(P_DISPLAY_NAME, profile.displayName())
                .set(P_TIMEZONE, profile.timezone())
                .set(P_LOCALE, profile.locale())
                .set(P_TELEGRAM_CHAT_ID, profile.telegramChatId())
                .set(DSL.field("user_profiles.created_at", OffsetDateTime.class), now)
                .set(DSL.field("user_profiles.updated_at", OffsetDateTime.class), now)
                .execute();

        return user;
    }

    @Override
    public User update(User user) {
        OffsetDateTime updatedAt = user.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(USERS)
                .set(U_EMAIL, user.getEmail())
                .set(U_USERNAME, user.getUsername())
                .set(U_PASSWORD_HASH, user.getPasswordHash())
                .set(U_ROLE, user.getRole().name())
                .set(U_ENABLED, user.isEnabled())
                .set(U_UPDATED_AT, updatedAt)
                .set(U_DELETED_AT, user.getDeletedAt() != null
                        ? user.getDeletedAt().atOffset(ZoneOffset.UTC)
                        : null)
                .where(U_ID.eq(user.getId().value()))
                .execute();

        UserProfile profile = user.getProfile();
        dsl.update(USER_PROFILES)
                .set(P_DISPLAY_NAME, profile.displayName())
                .set(P_TIMEZONE, profile.timezone())
                .set(P_LOCALE, profile.locale())
                .set(P_TELEGRAM_CHAT_ID, profile.telegramChatId())
                .set(DSL.field("user_profiles.updated_at", OffsetDateTime.class), updatedAt)
                .where(P_USER_ID.eq(user.getId().value()))
                .execute();

        return user;
    }

    @Override
    public UserPage findAll(String status, String search, int page, int size) {
        Condition condition = DSL.trueCondition();

        if (status != null) {
            condition = switch (status) {
                case "active" -> condition.and(U_ENABLED.isTrue()).and(U_DELETED_AT.isNull());
                case "banned" -> condition.and(U_ENABLED.isFalse()).and(U_DELETED_AT.isNull());
                case "deleted" -> condition.and(U_DELETED_AT.isNotNull());
                default -> condition;
            };
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            condition = condition.and(
                    DSL.lower(U_EMAIL).like(pattern)
                            .or(DSL.lower(U_USERNAME).like(pattern))
            );
        }

        long totalElements = dsl.selectCount()
                .from(USERS)
                .where(condition)
                .fetchOneInto(Long.class);

        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<User> content = dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(U_ID.eq(P_USER_ID))
                .where(condition)
                .orderBy(U_CREATED_AT.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::mapToUser);

        return new UserPage(content, totalElements, totalPages, page, size);
    }

    private User mapToUser(Record record) {
        UserId userId = new UserId(record.get(U_ID));
        UserProfile profile = new UserProfile(
                record.get(P_DISPLAY_NAME),
                record.get(P_TIMEZONE),
                record.get(P_LOCALE),
                record.get(P_TELEGRAM_CHAT_ID)
        );

        OffsetDateTime deletedAt = record.get(U_DELETED_AT);

        return new User(
                userId,
                record.get(U_EMAIL),
                record.get(U_USERNAME),
                record.get(U_PASSWORD_HASH),
                Role.valueOf(record.get(U_ROLE)),
                record.get(U_ENABLED),
                record.get(U_CREATED_AT).toInstant(),
                record.get(U_UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null,
                profile
        );
    }
}
