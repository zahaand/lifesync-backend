package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.InvalidCredentialsException;
import ru.zahaand.lifesync.domain.user.exception.UserBannedException;
import ru.zahaand.lifesync.domain.user.exception.UserDeletedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private LoginUserUseCase useCase;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final long ACCESS_TOKEN_EXPIRY = 900L;
    private static final long REFRESH_TOKEN_EXPIRY = 604800L;

    private User activeUser;

    @BeforeEach
    void setUp() {
        useCase = new LoginUserUseCase(userRepository, passwordEncoder, tokenProvider,
                refreshTokenRepository, FIXED_CLOCK, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);

        activeUser = new User(
                new UserId(UUID.randomUUID()),
                "user@example.com",
                "john_doe",
                "$2a$12$hash",
                Role.USER,
                true,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                new UserProfile(null, "UTC", "en", null)
        );
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should login successfully with email")
        void shouldLoginWithEmail() {
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("SecurePass1", "$2a$12$hash")).thenReturn(true);
            when(tokenProvider.generateAccessToken(activeUser)).thenReturn("access-jwt");
            when(tokenProvider.generateRefreshToken()).thenReturn(
                    new TokenProvider.TokenPair("raw-refresh", "hash-refresh"));

            LoginUserUseCase.LoginResult result = useCase.execute("user@example.com", "SecurePass1");

            assertThat(result.accessToken()).isEqualTo("access-jwt");
            assertThat(result.refreshToken()).isEqualTo("raw-refresh");
            verify(refreshTokenRepository).save(any(), anyString(), any());
        }

        @Test
        @DisplayName("Should login successfully with username")
        void shouldLoginWithUsername() {
            when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("SecurePass1", "$2a$12$hash")).thenReturn(true);
            when(tokenProvider.generateAccessToken(activeUser)).thenReturn("access-jwt");
            when(tokenProvider.generateRefreshToken()).thenReturn(
                    new TokenProvider.TokenPair("raw-refresh", "hash-refresh"));

            LoginUserUseCase.LoginResult result = useCase.execute("john_doe", "SecurePass1");

            assertThat(result.accessToken()).isEqualTo("access-jwt");
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when user not found")
        void shouldThrowOnUserNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute("unknown@example.com", "password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when password is wrong")
        void shouldThrowOnWrongPassword() {
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute("user@example.com", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should throw UserBannedException when user is banned")
        void shouldThrowOnBannedUser() {
            User bannedUser = activeUser.ban();
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(bannedUser));

            assertThatThrownBy(() -> useCase.execute("user@example.com", "SecurePass1"))
                    .isInstanceOf(UserBannedException.class);
        }

        @Test
        @DisplayName("Should throw UserDeletedException when user is deleted")
        void shouldThrowOnDeletedUser() {
            User deletedUser = activeUser.softDelete(Instant.now());
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(deletedUser));

            assertThatThrownBy(() -> useCase.execute("user@example.com", "SecurePass1"))
                    .isInstanceOf(UserDeletedException.class);
        }
    }
}
