package ru.zahaand.lifesync.domain.user;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {

    void save(UserId userId, String tokenHash, Instant expiresAt);

    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    void revokeByTokenHash(String tokenHash);

    void revokeAllByUserId(UserId userId);

    record RefreshTokenRecord(UserId userId, String tokenHash, Instant expiresAt, boolean revoked) {
    }
}
