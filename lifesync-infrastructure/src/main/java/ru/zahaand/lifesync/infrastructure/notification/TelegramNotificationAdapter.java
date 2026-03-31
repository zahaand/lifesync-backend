package ru.zahaand.lifesync.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class TelegramNotificationAdapter implements TelegramNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationAdapter.class);
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final boolean enabled;
    private final String botToken;
    private final HttpClient httpClient;

    public TelegramNotificationAdapter(
            @Value("${lifesync.telegram.enabled:false}") boolean enabled,
            @Value("${lifesync.telegram.bot-token:}") String botToken) {
        this.enabled = enabled;
        this.botToken = botToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void send(String chatId, String message) {
        if (!enabled) {
            log.info("Telegram disabled, would send to chatId={}: {}", chatId, message);
            return;
        }

        String url = String.format(TELEGRAM_API_URL, botToken);
        String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Telegram API returned status " + response.statusCode()
                        + ": " + response.body());
            }
            log.debug("Telegram message sent to chatId={}", chatId);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to send Telegram message to chatId=" + chatId, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Telegram send interrupted for chatId=" + chatId, ex);
        }
    }
}
