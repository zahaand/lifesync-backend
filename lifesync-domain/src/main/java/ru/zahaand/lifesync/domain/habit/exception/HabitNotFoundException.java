package ru.zahaand.lifesync.domain.habit.exception;

public class HabitNotFoundException extends RuntimeException {

    public HabitNotFoundException(String message) {
        super(message);
    }
}
