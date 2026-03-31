package ru.zahaand.lifesync.web.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
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
import ru.zahaand.lifesync.web.BaseIT;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.PROCESSED_EVENTS;

class IdempotencyIT extends BaseIT {

    private static final String TOPIC = "habit.log.completed";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private UUID userId;
    private UUID habitUuid;

    @BeforeEach
    void setUp() throws Exception {
        String email = "idem_it_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "idem_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        String accessToken = loginAndGetAccessToken(email, "SecurePass1");
        String habitId = createHabit(accessToken, "Idempotency test habit");
        habitUuid = UUID.fromString(habitId);

        CompleteHabitRequestDto completeRequest = new CompleteHabitRequestDto()
                .date(LocalDate.of(2026, 3, 31));
        mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isCreated());

        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    int count = dsl.fetchCount(
                            dsl.selectFrom(PROCESSED_EVENTS)
                                    .where(PROCESSED_EVENTS.CONSUMER_GROUP.eq("lifesync-streak-calculator"))
                                    .and(PROCESSED_EVENTS.EVENT_TYPE.eq("HabitCompletedEvent"))
                    );
                    assertThat(count).isGreaterThanOrEqualTo(1);
                });

        userId = dsl.select(org.jooq.impl.DSL.field("user_id", UUID.class))
                .from(org.jooq.impl.DSL.table("habits"))
                .where(org.jooq.impl.DSL.field("id", UUID.class).eq(habitUuid))
                .fetchOneInto(UUID.class);
    }

    @Nested
    class DuplicateEvent {

        @Test
        @DisplayName("Should skip duplicate event and not create extra processed_events records")
        void shouldSkipDuplicateEvent() {
            String fixedEventId = "duplicate-test-" + UUID.randomUUID();

            HabitCompletedEvent event = new HabitCompletedEvent(
                    fixedEventId,
                    habitUuid,
                    userId,
                    LocalDate.of(2026, 3, 31),
                    UUID.randomUUID(),
                    Instant.parse("2026-03-31T12:00:00Z")
            );

            kafkaTemplate.send(TOPIC, habitUuid.toString(), event);

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        int count = dsl.fetchCount(
                                dsl.selectFrom(PROCESSED_EVENTS)
                                        .where(PROCESSED_EVENTS.EVENT_ID.eq(fixedEventId))
                                        .and(PROCESSED_EVENTS.CONSUMER_GROUP.eq("lifesync-streak-calculator"))
                        );
                        assertThat(count).isEqualTo(1);
                    });

            kafkaTemplate.send(TOPIC, habitUuid.toString(), event);

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int finalCount = dsl.fetchCount(
                    dsl.selectFrom(PROCESSED_EVENTS)
                            .where(PROCESSED_EVENTS.EVENT_ID.eq(fixedEventId))
                            .and(PROCESSED_EVENTS.CONSUMER_GROUP.eq("lifesync-streak-calculator"))
            );
            assertThat(finalCount).isEqualTo(1);
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

    private String createHabit(String accessToken, String title) throws Exception {
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
}
