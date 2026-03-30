package ru.zahaand.lifesync.domain.user;

public interface TokenProvider {

    String generateAccessToken(User user);

    TokenPair generateRefreshToken();

    TokenClaims validateAccessToken(String token);

    record TokenPair(String rawToken, String tokenHash) {
    }

    record TokenClaims(UserId userId, String email, Role role) {
    }
}
