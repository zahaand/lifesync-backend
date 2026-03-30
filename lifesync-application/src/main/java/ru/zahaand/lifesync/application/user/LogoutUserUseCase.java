package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;

public class LogoutUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUserUseCase.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutUserUseCase(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public void execute(String rawRefreshToken) {
        String tokenHash = computeSha256(rawRefreshToken);
        refreshTokenRepository.revokeByTokenHash(tokenHash);
        log.info("User logged out successfully");
    }

    private String computeSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
