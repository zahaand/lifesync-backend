package ru.zahaand.lifesync.infrastructure.user;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.UserId;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqRefreshTokenRepository implements RefreshTokenRepository {

    private static final Table<?> REFRESH_TOKENS = DSL.table("refresh_tokens");

    private static final Field<UUID> RT_ID = DSL.field("refresh_tokens.id", UUID.class);
    private static final Field<UUID> RT_USER_ID = DSL.field("refresh_tokens.user_id", UUID.class);
    private static final Field<String> RT_TOKEN_HASH = DSL.field("refresh_tokens.token_hash", String.class);
    private static final Field<OffsetDateTime> RT_EXPIRES_AT = DSL.field("refresh_tokens.expires_at", OffsetDateTime.class);
    private static final Field<Boolean> RT_REVOKED = DSL.field("refresh_tokens.revoked", Boolean.class);

    private final DSLContext dsl;

    public JooqRefreshTokenRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(UserId userId, String tokenHash, Instant expiresAt) {
        dsl.insertInto(REFRESH_TOKENS)
                .set(RT_ID, UUID.randomUUID())
                .set(RT_USER_ID, userId.value())
                .set(RT_TOKEN_HASH, tokenHash)
                .set(RT_EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .set(RT_REVOKED, false)
                .execute();
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return dsl.select(RT_USER_ID, RT_TOKEN_HASH, RT_EXPIRES_AT, RT_REVOKED)
                .from(REFRESH_TOKENS)
                .where(RT_TOKEN_HASH.eq(tokenHash))
                .fetchOptional(record -> new RefreshTokenRecord(
                        new UserId(record.get(RT_USER_ID)),
                        record.get(RT_TOKEN_HASH),
                        record.get(RT_EXPIRES_AT).toInstant(),
                        record.get(RT_REVOKED)
                ));
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        dsl.update(REFRESH_TOKENS)
                .set(RT_REVOKED, true)
                .where(RT_TOKEN_HASH.eq(tokenHash))
                .execute();
    }

    @Override
    public void revokeAllByUserId(UserId userId) {
        dsl.update(REFRESH_TOKENS)
                .set(RT_REVOKED, true)
                .where(RT_USER_ID.eq(userId.value()))
                .and(RT_REVOKED.isFalse())
                .execute();
    }
}
