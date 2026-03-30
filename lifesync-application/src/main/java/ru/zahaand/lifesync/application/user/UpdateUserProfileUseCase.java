package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.Clock;
import java.time.ZoneId;

public class UpdateUserProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateUserProfileUseCase.class);

    private final UserRepository userRepository;
    private final Clock clock;

    public UpdateUserProfileUseCase(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public User execute(UserId userId, String displayName, String timezone, String locale) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));

        UserProfile profile = user.getProfile();

        if (displayName != null) {
            profile = profile.withDisplayName(displayName);
        }
        if (timezone != null) {
            validateTimezone(timezone);
            profile = profile.withTimezone(timezone);
        }
        if (locale != null) {
            validateLocale(locale);
            profile = profile.withLocale(locale);
        }

        User updated = user.withProfile(profile).withUpdatedAt(clock.instant());
        User saved = userRepository.update(updated);
        log.info("User profile updated: userId={}", userId.value());
        return saved;
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    private void validateLocale(String locale) {
        if (!locale.matches("^[a-z]{2}(-[A-Z]{2})?$")) {
            throw new IllegalArgumentException("Invalid locale: " + locale);
        }
    }
}
