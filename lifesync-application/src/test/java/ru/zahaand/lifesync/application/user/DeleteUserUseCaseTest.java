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
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private DeleteUserUseCase useCase;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private UserId userId;

    @BeforeEach
    void setUp() {
        useCase = new DeleteUserUseCase(userRepository, refreshTokenRepository, FIXED_CLOCK);
        userId = new UserId(UUID.randomUUID());
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should soft delete user and revoke all tokens")
        void shouldSoftDeleteUser() {
            User user = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                    Role.USER, true, Instant.now(), Instant.now(), null,
                    new UserProfile(null, "UTC", "en", null));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            useCase.execute(userId);

            verify(userRepository).update(argThat(User::isDeleted));
            verify(refreshTokenRepository).revokeAllByUserId(userId);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}
