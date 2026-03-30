package ru.zahaand.lifesync.infrastructure.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    private static final int BCRYPT_COST = 12;

    private final BCryptPasswordEncoder delegate;

    public BcryptPasswordEncoder() {
        this.delegate = new BCryptPasswordEncoder(BCRYPT_COST);
    }

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
