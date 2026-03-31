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
import ru.zahaand.lifesync.api.model.GoalCreateRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.notification.GoalSentMilestoneRepository;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GoalNotificationConsumer integration tests")
class GoalNotificationConsumerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private GoalSentMilestoneRepository goalSentMilestoneRepository;

    private String accessToken;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "goaln_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "goaln_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        userId = getUserId();
        setTelegramChatId("123456789");
    }

    @Nested
    class Consume {

        @Test
        @DisplayName("Should create milestone row at 25 percent")
        void shouldCreateMilestoneRowAt25Percent() throws Exception {
            String goalId = createGoal("Milestone test goal");
            UUID goalUuid = UUID.fromString(goalId);
            String eventId = UUID.randomUUID().toString();

            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, goalUuid, userId, null, 25, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    goalUuid.toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-notifier"))
                    );

            GoalId gId = new GoalId(goalUuid);
            assertTrue(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 25));
        }

        @Test
        @DisplayName("Should create multiple milestone rows on progress jump")
        void shouldCreateMultipleMilestoneRowsOnJump() throws Exception {
            String goalId = createGoal("Jump test goal");
            UUID goalUuid = UUID.fromString(goalId);
            String eventId = UUID.randomUUID().toString();

            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, goalUuid, userId, null, 75, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    goalUuid.toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-notifier"))
                    );

            GoalId gId = new GoalId(goalUuid);
            assertTrue(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 25));
            assertTrue(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 50));
            assertTrue(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 75));
            assertFalse(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 100));
        }

        @Test
        @DisplayName("Should handle duplicate event idempotently")
        void shouldHandleDuplicateEvent() throws Exception {
            String goalId = createGoal("Dedup test goal");
            UUID goalUuid = UUID.fromString(goalId);
            String eventId = UUID.randomUUID().toString();

            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, goalUuid, userId, null, 25, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    goalUuid.toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-notifier"))
                    );

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    goalUuid.toString(), event));

            Thread.sleep(2000);

            GoalId gId = new GoalId(goalUuid);
            assertTrue(goalSentMilestoneRepository.existsByGoalIdAndThreshold(gId, 25));
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

    private String createGoal(String title) throws Exception {
        GoalCreateRequestDto request = new GoalCreateRequestDto()
                .title(title);

        String response = mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
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
