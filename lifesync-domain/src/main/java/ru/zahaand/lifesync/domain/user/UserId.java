package ru.zahaand.lifesync.domain.user;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId must not be null");
        }
    }
}
