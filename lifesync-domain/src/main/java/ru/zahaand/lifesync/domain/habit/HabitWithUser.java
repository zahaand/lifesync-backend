package ru.zahaand.lifesync.domain.habit;

public record HabitWithUser(Habit habit, String telegramChatId, String timezone) {
}
