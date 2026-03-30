package ru.zahaand.lifesync.infrastructure.user;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.exception.InvalidTokenException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final long accessTokenExpiry;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public JwtTokenProvider(@Value("${jwt.private-key}") String privateKeyPem,
                            @Value("${jwt.public-key}") String publicKeyPem,
                            @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
                            Clock clock) {
        try {
            String resolvedPrivateKey = resolveKeyValue(privateKeyPem);
            String resolvedPublicKey = resolveKeyValue(publicKeyPem);

            JWK privateJwk = JWK.parseFromPEMEncodedObjects(resolvedPrivateKey);
            RSAKey rsaKey = privateJwk.toRSAKey();
            this.signer = new RSASSASigner(rsaKey);

            JWK publicJwk = JWK.parseFromPEMEncodedObjects(resolvedPublicKey);
            RSAPublicKey rsaPublicKey = publicJwk.toRSAKey().toRSAPublicKey();
            this.verifier = new RSASSAVerifier(rsaPublicKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA keys", e);
        }

        this.accessTokenExpiry = accessTokenExpiry;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().value().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenExpiry)))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            signedJWT.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return signedJWT.serialize();
    }

    @Override
    public TokenPair generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = computeSha256(rawToken);
        return new TokenPair(rawToken, tokenHash);
    }

    @Override
    public TokenClaims validateAccessToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                throw new InvalidTokenException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || Date.from(clock.instant()).after(expiration)) {
                throw new InvalidTokenException("Token has expired");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.getStringClaim("email");
            Role role = Role.valueOf(claims.getStringClaim("role"));

            return new TokenClaims(new UserId(userId), email, role);
        } catch (ParseException | JOSEException e) {
            throw new InvalidTokenException("Invalid token: " + e.getMessage());
        }
    }

    private static String resolveKeyValue(String value) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(value);
        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read key resource: " + value, e);
            }
        }
        return value;
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
