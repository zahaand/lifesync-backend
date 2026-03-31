package ru.zahaand.lifesync.infrastructure.user;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.infrastructure.generated.tables.UserProfiles;
import ru.zahaand.lifesync.infrastructure.generated.tables.Users;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.USERS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.USER_PROFILES;

@Repository
public class JooqUserRepository implements UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(USERS.ID.eq(USER_PROFILES.USER_ID))
                .where(USERS.ID.eq(id.value()))
                .and(USERS.DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(USERS.ID.eq(USER_PROFILES.USER_ID))
                .where(USERS.EMAIL.eq(email))
                .and(USERS.DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(USERS.ID.eq(USER_PROFILES.USER_ID))
                .where(DSL.lower(USERS.USERNAME).eq(username.toLowerCase()))
                .and(USERS.DELETED_AT.isNull())
                .fetchOptional(this::mapToUser);
    }

    @Override
    public boolean existsByEmail(String email) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USERS)
                        .where(USERS.EMAIL.eq(email))
                        .and(USERS.DELETED_AT.isNull())
        );
    }

    @Override
    public boolean existsByUsername(String username) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USERS)
                        .where(DSL.lower(USERS.USERNAME).eq(username.toLowerCase()))
                        .and(USERS.DELETED_AT.isNull())
        );
    }

    @Override
    public User save(User user) {
        UUID userId = user.getId().value();
        OffsetDateTime now = user.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.USERNAME, user.getUsername())
                .set(USERS.PASSWORD_HASH, user.getPasswordHash())
                .set(USERS.ROLE, user.getRole().name())
                .set(USERS.ENABLED, user.isEnabled())
                .set(USERS.CREATED_AT, now)
                .set(USERS.UPDATED_AT, now)
                .execute();

        UserProfile profile = user.getProfile();
        dsl.insertInto(USER_PROFILES)
                .set(USER_PROFILES.ID, UUID.randomUUID())
                .set(USER_PROFILES.USER_ID, userId)
                .set(USER_PROFILES.DISPLAY_NAME, profile.displayName())
                .set(USER_PROFILES.TIMEZONE, profile.timezone())
                .set(USER_PROFILES.LOCALE, profile.locale())
                .set(USER_PROFILES.TELEGRAM_CHAT_ID, profile.telegramChatId())
                .set(USER_PROFILES.CREATED_AT, now)
                .set(USER_PROFILES.UPDATED_AT, now)
                .execute();

        return user;
    }

    @Override
    public User update(User user) {
        OffsetDateTime updatedAt = user.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(USERS)
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.USERNAME, user.getUsername())
                .set(USERS.PASSWORD_HASH, user.getPasswordHash())
                .set(USERS.ROLE, user.getRole().name())
                .set(USERS.ENABLED, user.isEnabled())
                .set(USERS.UPDATED_AT, updatedAt)
                .set(USERS.DELETED_AT, user.getDeletedAt() != null
                        ? user.getDeletedAt().atOffset(ZoneOffset.UTC)
                        : null)
                .where(USERS.ID.eq(user.getId().value()))
                .execute();

        UserProfile profile = user.getProfile();
        dsl.update(USER_PROFILES)
                .set(USER_PROFILES.DISPLAY_NAME, profile.displayName())
                .set(USER_PROFILES.TIMEZONE, profile.timezone())
                .set(USER_PROFILES.LOCALE, profile.locale())
                .set(USER_PROFILES.TELEGRAM_CHAT_ID, profile.telegramChatId())
                .set(USER_PROFILES.UPDATED_AT, updatedAt)
                .where(USER_PROFILES.USER_ID.eq(user.getId().value()))
                .execute();

        return user;
    }

    @Override
    public UserPage findAll(String status, String search, int page, int size) {
        Condition condition = DSL.trueCondition();

        if (status != null) {
            condition = switch (status) {
                case "active" -> condition.and(USERS.ENABLED.isTrue()).and(USERS.DELETED_AT.isNull());
                case "banned" -> condition.and(USERS.ENABLED.isFalse()).and(USERS.DELETED_AT.isNull());
                case "deleted" -> condition.and(USERS.DELETED_AT.isNotNull());
                default -> condition;
            };
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            condition = condition.and(
                    DSL.lower(USERS.EMAIL).like(pattern)
                            .or(DSL.lower(USERS.USERNAME).like(pattern))
            );
        }

        long totalElements = dsl.selectCount()
                .from(USERS)
                .where(condition)
                .fetchOneInto(Long.class);

        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<User> content = dsl.select()
                .from(USERS)
                .leftJoin(USER_PROFILES).on(USERS.ID.eq(USER_PROFILES.USER_ID))
                .where(condition)
                .orderBy(USERS.CREATED_AT.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::mapToUser);

        return new UserPage(content, totalElements, totalPages, page, size);
    }

    private User mapToUser(Record record) {
        UserId userId = new UserId(record.get(USERS.ID));
        UserProfile profile = new UserProfile(
                record.get(USER_PROFILES.DISPLAY_NAME),
                record.get(USER_PROFILES.TIMEZONE),
                record.get(USER_PROFILES.LOCALE),
                record.get(USER_PROFILES.TELEGRAM_CHAT_ID)
        );

        OffsetDateTime deletedAt = record.get(USERS.DELETED_AT);

        return new User(
                userId,
                record.get(USERS.EMAIL),
                record.get(USERS.USERNAME),
                record.get(USERS.PASSWORD_HASH),
                Role.valueOf(record.get(USERS.ROLE)),
                record.get(USERS.ENABLED),
                record.get(USERS.CREATED_AT).toInstant(),
                record.get(USERS.UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null,
                profile
        );
    }
}
