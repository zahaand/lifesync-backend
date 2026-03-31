package ru.zahaand.lifesync.web.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.CompleteHabitRequestDto;
import ru.zahaand.lifesync.api.model.CreateHabitRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABIT_STREAKS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.PROCESSED_EVENTS;

class KafkaIntegrationIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    private String accessToken;
    private String habitId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "kafka_it_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "kafka_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        habitId = createHabit("Kafka test habit");
    }

    @Nested
    class CompleteHabitEventFlow {

        @Test
        @DisplayName("Should publish HabitCompletedEvent and recalculate streak after habit completion")
        void shouldPublishEventAndRecalculateStreak() throws Exception {
            LocalDate today = LocalDate.of(2026, 3, 31);

            CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                    .date(today);

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            UUID habitUuid = UUID.fromString(habitId);

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        Integer currentStreak = dsl.select(HABIT_STREAKS.CURRENT_STREAK)
                                .from(HABIT_STREAKS)
                                .where(HABIT_STREAKS.HABIT_ID.eq(habitUuid))
                                .fetchOneInto(Integer.class);
                        assertThat(currentStreak).isNotNull().isGreaterThanOrEqualTo(1);
                    });

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        int processedCount = dsl.fetchCount(
                                dsl.selectFrom(PROCESSED_EVENTS)
                                        .where(PROCESSED_EVENTS.EVENT_TYPE.eq("HabitCompletedEvent"))
                                        .and(PROCESSED_EVENTS.CONSUMER_GROUP.in(
                                                "lifesync-streak-calculator",
                                                "lifesync-analytics-updater",
                                                "lifesync-telegram-notifier"
                                        ))
                        );
                        assertThat(processedCount).isGreaterThanOrEqualTo(3);
                    });
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
}
