package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.Clock;

public class BanUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(BanUserUseCase.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public BanUserUseCase(UserRepository userRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public User execute(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));

        User banned = user.ban().withUpdatedAt(clock.instant());
        User saved = userRepository.update(banned);
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User banned: userId={}", userId.value());
        return saved;
    }
}
