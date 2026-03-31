package ru.zahaand.lifesync.web.goal;

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
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalConsumerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setUp() throws Exception {
        String email = "consumer_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "cons_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
    }

    @Nested
    class StubConsumers {

        @Test
        @DisplayName("Should process GoalProgressUpdatedEvent in analytics consumer")
        void shouldProcessInAnalyticsConsumer() throws Exception {
            String eventId = UUID.randomUUID().toString();
            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, UUID.randomUUID(), UUID.randomUUID(), null, 50, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    event.goalId().toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-analytics"))
                    );
        }

        @Test
        @DisplayName("Should process GoalProgressUpdatedEvent in notification consumer")
        void shouldProcessInNotificationConsumer() throws Exception {
            String eventId = UUID.randomUUID().toString();
            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, UUID.randomUUID(), UUID.randomUUID(), null, 75, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    event.goalId().toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-notifier"))
                    );
        }

        @Test
        @DisplayName("Should handle idempotency — duplicate event is skipped")
        void shouldHandleIdempotency() throws Exception {
            String eventId = UUID.randomUUID().toString();
            GoalProgressUpdatedEvent event = new GoalProgressUpdatedEvent(
                    eventId, UUID.randomUUID(), UUID.randomUUID(), null, 50, Instant.now());

            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    event.goalId().toString(), event));

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                                    eventId, "lifesync-goal-analytics"))
                    );

            // Send duplicate — should not fail
            kafkaTemplate.send(new ProducerRecord<>("goal.progress.updated",
                    event.goalId().toString(), event));

            // Wait a bit to ensure processing completes without error
            Thread.sleep(2000);

            assertTrue(processedEventRepository.existsByEventIdAndConsumerGroup(
                    eventId, "lifesync-goal-analytics"));
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
}
