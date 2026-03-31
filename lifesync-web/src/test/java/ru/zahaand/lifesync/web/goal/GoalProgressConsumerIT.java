package ru.zahaand.lifesync.web.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.CompleteHabitRequestDto;
import ru.zahaand.lifesync.api.model.CreateHabitRequestDto;
import ru.zahaand.lifesync.api.model.GoalCreateRequestDto;
import ru.zahaand.lifesync.api.model.GoalHabitLinkRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalProgressConsumerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "kafka_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "kafka_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
    }

    @Nested
    class EndToEndKafkaFlow {

        @Test
        @DisplayName("Should recalculate goal progress after habit completion via Kafka")
        void shouldRecalculateProgressAfterCompletion() throws Exception {
            String goalId = createGoal("Kafka progress goal");
            String habitId = createHabit("Kafka habit");
            linkHabitToGoal(goalId, habitId);

            completeHabit(habitId, LocalDate.now());

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                                            .header("Authorization", "Bearer " + accessToken))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.progress").value(org.hamcrest.Matchers.greaterThan(0)))
                    );
        }

        @Test
        @DisplayName("Should recalculate multiple goals linked to same habit")
        void shouldRecalculateMultipleGoals() throws Exception {
            String goalId1 = createGoal("Goal 1");
            String goalId2 = createGoal("Goal 2");
            String habitId = createHabit("Shared habit");
            linkHabitToGoal(goalId1, habitId);
            linkHabitToGoal(goalId2, habitId);

            completeHabit(habitId, LocalDate.now());

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        mockMvc.perform(get("/api/v1/goals/{goalId}", goalId1)
                                        .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.progress").value(org.hamcrest.Matchers.greaterThan(0)));

                        mockMvc.perform(get("/api/v1/goals/{goalId}", goalId2)
                                        .header("Authorization", "Bearer " + accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.progress").value(org.hamcrest.Matchers.greaterThan(0)));
                    });
        }

        @Test
        @DisplayName("Should recalculate progress after habit log deletion")
        void shouldRecalculateAfterLogDeletion() throws Exception {
            String goalId = createGoal("Deletion test goal");
            String habitId = createHabit("Deletion test habit");
            linkHabitToGoal(goalId, habitId);

            String logId = completeHabit(habitId, LocalDate.now());

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                                            .header("Authorization", "Bearer " + accessToken))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.progress").value(org.hamcrest.Matchers.greaterThan(0)))
                    );

            mockMvc.perform(delete("/api/v1/habits/{id}/complete/{logId}", habitId, logId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                                            .header("Authorization", "Bearer " + accessToken))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.progress").value(0))
                    );
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

    private void linkHabitToGoal(String goalId, String habitId) throws Exception {
        GoalHabitLinkRequestDto request = new GoalHabitLinkRequestDto()
                .habitId(UUID.fromString(habitId));

        mockMvc.perform(post("/api/v1/goals/{goalId}/habits", goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String completeHabit(String habitId, LocalDate date) throws Exception {
        CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                .date(date);

        String response = mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
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
