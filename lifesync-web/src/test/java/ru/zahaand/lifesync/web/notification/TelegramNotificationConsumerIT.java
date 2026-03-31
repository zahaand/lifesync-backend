package ru.zahaand.lifesync.web.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.CompleteHabitRequestDto;
import ru.zahaand.lifesync.api.model.CreateHabitRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;
import ru.zahaand.lifesync.web.BaseIT;
import ru.zahaand.lifesync.web.TestTelegramNotificationSender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TelegramNotificationConsumer integration tests")
class TelegramNotificationConsumerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private TestTelegramNotificationSender testTelegramNotificationSender;

    private String accessToken;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        testTelegramNotificationSender.clear();
        String email = "tgn_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "tgn_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        userId = getUserId();
        setTelegramChatId("999888777");
    }

    @Nested
    class Consume {

        @Test
        @DisplayName("Should send notification with habit title for streak milestone")
        void shouldSendNotificationWithHabitTitle() throws Exception {
            String habitId = createHabit("Morning run");
            UUID habitUuid = UUID.fromString(habitId);

            for (int i = 0; i < 7; i++) {
                LocalDate date = LocalDate.of(2026, 3, 25).plusDays(i);
                completeHabit(habitId, date);
            }

            String eventId = UUID.randomUUID().toString();
            HabitCompletedEvent event = new HabitCompletedEvent(
                    eventId, habitUuid, userId, LocalDate.of(2026, 3, 31),
                    UUID.randomUUID(), Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("habit.log.completed",
                    habitUuid.toString(), event));

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-telegram-notifier"))
                    );

            boolean containsHabitTitle = testTelegramNotificationSender.getSentMessages().stream()
                    .anyMatch(msg -> msg.message().contains("Morning run"));
            assertTrue(containsHabitTitle, "Expected a message containing 'Morning run'");
        }
    }

    private void registerUser(String email, String username, String password) throws Exception {
        RegisterRequestDto request = new RegisterRequestDto()
                .email(email)
                .username(username)
                .password(password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        LoginRequestDto request = new LoginRequestDto()
                .identifier(email)
                .password(password);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID getUserId() throws Exception {
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createHabit(String title) throws Exception {
        CreateHabitRequestDto request = new CreateHabitRequestDto()
                .title(title)
                .frequency(CreateHabitRequestDto.FrequencyEnum.DAILY);

        String response = mockMvc.perform(post("/api/v1/habits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    private void completeHabit(String habitId, LocalDate date) throws Exception {
        CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                .date(date);

        mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void setTelegramChatId(String chatId) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("telegramChatId", chatId));
        mockMvc.perform(put("/api/v1/users/me/telegram")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
