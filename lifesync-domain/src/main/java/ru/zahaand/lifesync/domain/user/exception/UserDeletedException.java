package ru.zahaand.lifesync.domain.user.exception;

public class UserDeletedException extends RuntimeException {

    public UserDeletedException(String message) {
        super(message);
    }
}
