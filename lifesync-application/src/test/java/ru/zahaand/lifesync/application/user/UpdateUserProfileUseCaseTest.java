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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private UpdateUserProfileUseCase useCase;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private UserId userId;
    private User existingUser;

    @BeforeEach
    void setUp() {
        useCase = new UpdateUserProfileUseCase(userRepository, FIXED_CLOCK);
        userId = new UserId(UUID.randomUUID());
        existingUser = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                Role.USER, true, Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"), null,
                new UserProfile(null, "UTC", "en", null));
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should update all profile fields")
        void shouldUpdateAllFields() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = useCase.execute(userId, "John Doe", "Europe/Moscow", "ru");

            assertThat(result.getProfile().displayName()).isEqualTo("John Doe");
            assertThat(result.getProfile().timezone()).isEqualTo("Europe/Moscow");
            assertThat(result.getProfile().locale()).isEqualTo("ru");
        }

        @Test
        @DisplayName("Should update single field leaving others unchanged")
        void shouldUpdateSingleField() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = useCase.execute(userId, "New Name", null, null);

            assertThat(result.getProfile().displayName()).isEqualTo("New Name");
            assertThat(result.getProfile().timezone()).isEqualTo("UTC");
            assertThat(result.getProfile().locale()).isEqualTo("en");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid timezone")
        void shouldThrowOnInvalidTimezone() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> useCase.execute(userId, null, "Invalid/Zone", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid timezone");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid locale")
        void shouldThrowOnInvalidLocale() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> useCase.execute(userId, null, null, "invalid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid locale");
        }
    }
}
