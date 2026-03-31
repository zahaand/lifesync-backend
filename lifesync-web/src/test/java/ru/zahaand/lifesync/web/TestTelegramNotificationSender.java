package ru.zahaand.lifesync.web;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Primary
public class TestTelegramNotificationSender implements TelegramNotificationSender {

    private final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();

    @Override
    public void send(String chatId, String message) {
        sentMessages.add(new SentMessage(chatId, message));
    }

    public List<SentMessage> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    public void clear() {
        sentMessages.clear();
    }

    public record SentMessage(String chatId, String message) {
    }
}
