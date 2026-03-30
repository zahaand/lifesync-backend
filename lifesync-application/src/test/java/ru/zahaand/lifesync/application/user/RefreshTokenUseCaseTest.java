package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.InvalidTokenException;
import ru.zahaand.lifesync.domain.user.exception.UserBannedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenProvider tokenProvider;

    private RefreshTokenUseCase useCase;

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long ACCESS_TOKEN_EXPIRY = 900L;
    private static final long REFRESH_TOKEN_EXPIRY = 604800L;

    private UserId userId;
    private User activeUser;

    @BeforeEach
    void setUp() {
        useCase = new RefreshTokenUseCase(refreshTokenRepository, userRepository,
                tokenProvider, FIXED_CLOCK, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);

        userId = new UserId(UUID.randomUUID());
        activeUser = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                Role.USER, true, Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"), null,
                new UserProfile(null, "UTC", "en", null));
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should rotate refresh token successfully")
        void shouldRotateTokenSuccessfully() {
            String rawToken = "valid-raw-token";
            String tokenHash = computeSha256(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(new RefreshTokenRepository.RefreshTokenRecord(
                            userId, tokenHash, NOW.plusSeconds(3600), false)));
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
            when(tokenProvider.generateAccessToken(activeUser)).thenReturn("new-access");
            when(tokenProvider.generateRefreshToken()).thenReturn(
                    new TokenProvider.TokenPair("new-raw", "new-hash"));

            RefreshTokenUseCase.RefreshResult result = useCase.execute(rawToken);

            assertThat(result.accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-raw");
            verify(refreshTokenRepository).revokeByTokenHash(tokenHash);
            verify(refreshTokenRepository).save(any(), anyString(), any());
        }

        @Test
        @DisplayName("Should throw InvalidTokenException when token is revoked")
        void shouldThrowOnRevokedToken() {
            String rawToken = "revoked-token";
            String tokenHash = computeSha256(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(new RefreshTokenRepository.RefreshTokenRecord(
                            userId, tokenHash, NOW.plusSeconds(3600), true)));

            assertThatThrownBy(() -> useCase.execute(rawToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("revoked");
        }

        @Test
        @DisplayName("Should throw InvalidTokenException when token is expired")
        void shouldThrowOnExpiredToken() {
            String rawToken = "expired-token";
            String tokenHash = computeSha256(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(new RefreshTokenRepository.RefreshTokenRecord(
                            userId, tokenHash, NOW.minusSeconds(1), false)));

            assertThatThrownBy(() -> useCase.execute(rawToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should throw UserBannedException when user is banned")
        void shouldThrowOnBannedUser() {
            String rawToken = "valid-token";
            String tokenHash = computeSha256(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(new RefreshTokenRepository.RefreshTokenRecord(
                            userId, tokenHash, NOW.plusSeconds(3600), false)));
            when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser.ban()));

            assertThatThrownBy(() -> useCase.execute(rawToken))
                    .isInstanceOf(UserBannedException.class);
        }

        @Test
        @DisplayName("Should throw InvalidTokenException when token not found")
        void shouldThrowOnTokenNotFound() {
            String rawToken = "unknown-token";
            String tokenHash = computeSha256(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(rawToken))
                    .isInstanceOf(InvalidTokenException.class);
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
