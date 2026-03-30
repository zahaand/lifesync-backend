package ru.zahaand.lifesync.domain.habit.exception;

public class HabitInactiveException extends RuntimeException {

    public HabitInactiveException(String message) {
        super(message);
    }
}
