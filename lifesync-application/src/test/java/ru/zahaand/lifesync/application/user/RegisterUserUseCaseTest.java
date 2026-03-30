package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.DuplicateEmailException;
import ru.zahaand.lifesync.domain.user.exception.DuplicateUsernameException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterUserUseCase useCase;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(userRepository, passwordEncoder, FIXED_CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should register user successfully with valid data")
        void shouldRegisterUserSuccessfully() {
            when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
            when(userRepository.existsByUsername("john_doe")).thenReturn(false);
            when(passwordEncoder.encode("SecurePass1")).thenReturn("$2a$12$hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = useCase.execute("user@example.com", "john_doe", "SecurePass1");

            assertThat(result.getEmail()).isEqualTo("user@example.com");
            assertThat(result.getUsername()).isEqualTo("john_doe");
            assertThat(result.getRole()).isEqualTo(Role.USER);
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getProfile().timezone()).isEqualTo("UTC");
            assertThat(result.getProfile().locale()).isEqualTo("en");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw DuplicateEmailException when email already exists")
        void shouldThrowOnDuplicateEmail() {
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute("taken@example.com", "new_user", "SecurePass1"))
                    .isInstanceOf(DuplicateEmailException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw DuplicateUsernameException when username already exists")
        void shouldThrowOnDuplicateUsername() {
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.existsByUsername("taken_user")).thenReturn(true);

            assertThatThrownBy(() -> useCase.execute("new@example.com", "taken_user", "SecurePass1"))
                    .isInstanceOf(DuplicateUsernameException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid email format")
        void shouldThrowOnInvalidEmail() {
            assertThatThrownBy(() -> useCase.execute("not-an-email", "john_doe", "SecurePass1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid email");
            verify(userRepository, never()).existsByEmail(anyString());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for weak password without uppercase")
        void shouldThrowOnWeakPasswordNoUppercase() {
            assertThatThrownBy(() -> useCase.execute("user@example.com", "john_doe", "securepass1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("uppercase");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for weak password without digit")
        void shouldThrowOnWeakPasswordNoDigit() {
            assertThatThrownBy(() -> useCase.execute("user@example.com", "john_doe", "SecurePass"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("digit");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for too short password")
        void shouldThrowOnShortPassword() {
            assertThatThrownBy(() -> useCase.execute("user@example.com", "john_doe", "Sh1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 8");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for username too short")
        void shouldThrowOnUsernameTooShort() {
            assertThatThrownBy(() -> useCase.execute("user@example.com", "ab", "SecurePass1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for username too long")
        void shouldThrowOnUsernameTooLong() {
            String longUsername = "a".repeat(33);
            assertThatThrownBy(() -> useCase.execute("user@example.com", longUsername, "SecurePass1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for username with invalid characters")
        void shouldThrowOnUsernameInvalidChars() {
            assertThatThrownBy(() -> useCase.execute("user@example.com", "John Doe!", "SecurePass1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lowercase");
        }
    }
}
