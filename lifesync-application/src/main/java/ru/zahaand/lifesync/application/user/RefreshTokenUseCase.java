package ru.zahaand.lifesync.application.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.domain.user.exception.InvalidTokenException;
import ru.zahaand.lifesync.domain.user.exception.UserBannedException;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.Clock;

public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final Clock clock;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public RefreshTokenUseCase(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               TokenProvider tokenProvider,
                               Clock clock,
                               long accessTokenExpiry,
                               long refreshTokenExpiry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.clock = clock;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    @Transactional
    public RefreshResult execute(String rawRefreshToken) {
        String tokenHash = computeSha256(rawRefreshToken);

        RefreshTokenRepository.RefreshTokenRecord record = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (record.revoked()) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }
        if (record.expiresAt().isBefore(clock.instant())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        refreshTokenRepository.revokeByTokenHash(tokenHash);

        User user = userRepository.findById(record.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isBanned()) {
            throw new UserBannedException("Account is disabled");
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        TokenProvider.TokenPair newRefreshPair = tokenProvider.generateRefreshToken();

        refreshTokenRepository.save(
                user.getId(),
                newRefreshPair.tokenHash(),
                clock.instant().plusSeconds(refreshTokenExpiry)
        );

        log.info("Token refreshed successfully: userId={}", user.getId().value());
        return new RefreshResult(accessToken, newRefreshPair.rawToken(), (int) accessTokenExpiry);
    }

    private String computeSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RefreshResult(String accessToken, String refreshToken, int expiresIn) {
    }
}
