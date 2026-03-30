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

public class DeleteUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteUserUseCase.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public DeleteUserUseCase(UserRepository userRepository,
                             RefreshTokenRepository refreshTokenRepository,
                             Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));

        User deleted = user.softDelete(clock.instant());
        userRepository.update(deleted);
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User soft-deleted: userId={}", userId.value());
    }
}
