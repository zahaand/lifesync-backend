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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectTelegramUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private ConnectTelegramUseCase useCase;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private UserId userId;

    @BeforeEach
    void setUp() {
        useCase = new ConnectTelegramUseCase(userRepository, FIXED_CLOCK);
        userId = new UserId(UUID.randomUUID());
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should connect Telegram successfully")
        void shouldConnectTelegram() {
            User user = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                    Role.USER, true, Instant.now(), Instant.now(), null,
                    new UserProfile(null, "UTC", "en", null));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = useCase.execute(userId, "123456789");

            assertThat(result.getProfile().telegramChatId()).isEqualTo("123456789");
        }

        @Test
        @DisplayName("Should overwrite existing Telegram chat ID")
        void shouldOverwriteExistingChatId() {
            User user = new User(userId, "user@example.com", "john_doe", "$2a$12$hash",
                    Role.USER, true, Instant.now(), Instant.now(), null,
                    new UserProfile(null, "UTC", "en", "old_chat_id"));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = useCase.execute(userId, "new_chat_id");

            assertThat(result.getProfile().telegramChatId()).isEqualTo("new_chat_id");
        }
    }
}
