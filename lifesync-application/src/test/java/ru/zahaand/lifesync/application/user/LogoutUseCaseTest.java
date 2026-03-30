package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private LogoutUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUserUseCase(refreshTokenRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should revoke refresh token on logout")
        void shouldRevokeRefreshToken() {
            String rawToken = "valid-refresh-token";
            String expectedHash = computeSha256(rawToken);

            useCase.execute(rawToken);

            verify(refreshTokenRepository).revokeByTokenHash(expectedHash);
        }

        @Test
        @DisplayName("Should handle already revoked token gracefully (idempotent)")
        void shouldHandleAlreadyRevokedToken() {
            String rawToken = "already-revoked-token";
            String expectedHash = computeSha256(rawToken);

            useCase.execute(rawToken);

            verify(refreshTokenRepository).revokeByTokenHash(expectedHash);
        }

        @Test
        @DisplayName("Should handle unknown token gracefully (idempotent)")
        void shouldHandleUnknownToken() {
            String rawToken = "unknown-token";
            String expectedHash = computeSha256(rawToken);

            useCase.execute(rawToken);

            verify(refreshTokenRepository).revokeByTokenHash(expectedHash);
        }
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
