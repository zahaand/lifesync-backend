package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.DuplicateEmailException;
import ru.zahaand.lifesync.domain.user.exception.DuplicateUsernameException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCase.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final Pattern PASSWORD_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern PASSWORD_LOWER = Pattern.compile("[a-z]");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile("[0-9]");

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 32;
    private static final int PASSWORD_MIN_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User execute(String email, String username, String password) {
        validateEmail(email);
        validateUsername(username);
        validatePassword(password);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered: " + email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException("Username already taken: " + username);
        }

        String passwordHash = passwordEncoder.encode(password);
        Instant now = clock.instant();

        User user = new User(
                new UserId(UUID.randomUUID()),
                email,
                username,
                passwordHash,
                Role.USER,
                true,
                now,
                now,
                null,
                new UserProfile(null, "UTC", "en", null)
        );

        User saved = userRepository.save(user);
        log.info("User registered successfully: userId={}, email={}", saved.getId().value(), email);
        return saved;
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.length() < USERNAME_MIN_LENGTH
                || username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Username must be between " + USERNAME_MIN_LENGTH + " and " + USERNAME_MAX_LENGTH + " characters");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username may only contain lowercase letters, digits, hyphens, and underscores");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + PASSWORD_MIN_LENGTH + " characters");
        }
        if (!PASSWORD_UPPER.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!PASSWORD_LOWER.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!PASSWORD_DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
    }
}
