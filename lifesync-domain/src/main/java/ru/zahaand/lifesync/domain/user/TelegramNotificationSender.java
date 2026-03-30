package ru.zahaand.lifesync.domain.user;

public interface TelegramNotificationSender {

    void send(String chatId, String message);
}
