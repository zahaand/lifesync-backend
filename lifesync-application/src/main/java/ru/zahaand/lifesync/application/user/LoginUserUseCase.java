package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.InvalidCredentialsException;
import ru.zahaand.lifesync.domain.user.exception.UserBannedException;
import ru.zahaand.lifesync.domain.user.exception.UserDeletedException;

import java.time.Clock;

public class LoginUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUserUseCase.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public LoginUserUseCase(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TokenProvider tokenProvider,
                            RefreshTokenRepository refreshTokenRepository,
                            Clock clock,
                            long accessTokenExpiry,
                            long refreshTokenExpiry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public LoginResult execute(String identifier, String password) {
        boolean isEmail = identifier.contains("@");

        User user = isEmail
                ? userRepository.findByEmail(identifier)
                        .orElseThrow(() -> {
                            log.warn("Failed login attempt: user not found for email");
                            return new InvalidCredentialsException("Invalid credentials");
                        })
                : userRepository.findByUsername(identifier)
                        .orElseThrow(() -> {
                            log.warn("Failed login attempt: user not found for username");
                            return new InvalidCredentialsException("Invalid credentials");
                        });

        if (user.isDeleted()) {
            throw new UserDeletedException("User account has been deleted");
        }
        if (user.isBanned()) {
            throw new UserBannedException("Account is disabled");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Failed login attempt: wrong password for userId={}", user.getId().value());
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        TokenProvider.TokenPair refreshPair = tokenProvider.generateRefreshToken();

        refreshTokenRepository.save(
                user.getId(),
                refreshPair.tokenHash(),
                clock.instant().plusSeconds(refreshTokenExpiry)
        );

        log.info("User logged in successfully: userId={}", user.getId().value());
        return new LoginResult(accessToken, refreshPair.rawToken(), (int) accessTokenExpiry);
    }

    public record LoginResult(String accessToken, String refreshToken, int expiresIn) {
    }
}
