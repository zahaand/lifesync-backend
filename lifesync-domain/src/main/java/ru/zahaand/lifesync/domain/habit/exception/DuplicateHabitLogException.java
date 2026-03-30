package ru.zahaand.lifesync.domain.habit.exception;

public class DuplicateHabitLogException extends RuntimeException {

    public DuplicateHabitLogException(String message) {
        super(message);
    }
}
