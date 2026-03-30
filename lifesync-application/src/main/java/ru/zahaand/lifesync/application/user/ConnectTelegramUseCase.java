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

public class ConnectTelegramUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConnectTelegramUseCase.class);

    private final UserRepository userRepository;
    private final Clock clock;

    public ConnectTelegramUseCase(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public User execute(UserId userId, String telegramChatId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));

        UserProfile profile = user.getProfile().withTelegramChatId(telegramChatId);
        User updated = user.withProfile(profile).withUpdatedAt(clock.instant());
        User saved = userRepository.update(updated);
        log.info("Telegram connected: userId={}, chatId={}", userId.value(), telegramChatId);
        return saved;
    }
}
