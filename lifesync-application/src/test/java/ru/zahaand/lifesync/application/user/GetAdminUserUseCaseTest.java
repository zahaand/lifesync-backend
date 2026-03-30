package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdminUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private GetAdminUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAdminUserUseCase(userRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return user when found")
        void shouldReturnUser() {
            UserId userId = new UserId(UUID.randomUUID());
            User user = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                    Role.USER, true, Instant.now(), Instant.now(), null,
                    new UserProfile(null, "UTC", "en", null));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            User result = useCase.execute(userId);

            assertThat(result.getEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            UserId userId = new UserId(UUID.randomUUID());
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}
