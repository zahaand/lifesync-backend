package ru.zahaand.lifesync.infrastructure.user;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.UserId;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.REFRESH_TOKENS;

@Repository
public class JooqRefreshTokenRepository implements RefreshTokenRepository {

    private final DSLContext dsl;

    public JooqRefreshTokenRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(UserId userId, String tokenHash, Instant expiresAt) {
        dsl.insertInto(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.ID, UUID.randomUUID())
                .set(REFRESH_TOKENS.USER_ID, userId.value())
                .set(REFRESH_TOKENS.TOKEN_HASH, tokenHash)
                .set(REFRESH_TOKENS.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .set(REFRESH_TOKENS.REVOKED, false)
                .execute();
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return dsl.select(REFRESH_TOKENS.USER_ID, REFRESH_TOKENS.TOKEN_HASH,
                        REFRESH_TOKENS.EXPIRES_AT, REFRESH_TOKENS.REVOKED)
                .from(REFRESH_TOKENS)
                .where(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(record -> new RefreshTokenRecord(
                        new UserId(record.get(REFRESH_TOKENS.USER_ID)),
                        record.get(REFRESH_TOKENS.TOKEN_HASH),
                        record.get(REFRESH_TOKENS.EXPIRES_AT).toInstant(),
                        record.get(REFRESH_TOKENS.REVOKED)
                ));
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED, true)
                .where(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .execute();
    }

    @Override
    public void revokeAllByUserId(UserId userId) {
        dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED, true)
                .where(REFRESH_TOKENS.USER_ID.eq(userId.value()))
                .and(REFRESH_TOKENS.REVOKED.isFalse())
                .execute();
    }
}
