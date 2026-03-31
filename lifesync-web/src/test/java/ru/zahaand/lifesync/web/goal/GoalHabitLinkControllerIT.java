package ru.zahaand.lifesync.web.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.CreateHabitRequestDto;
import ru.zahaand.lifesync.api.model.GoalCreateRequestDto;
import ru.zahaand.lifesync.api.model.GoalHabitLinkRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalHabitLinkControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String goalId;
    private String habitId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "link_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "link_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        goalId = createGoal("Link test goal");
        habitId = createHabit("Link test habit");
    }

    @Nested
    class LinkHabit {

        @Test
        @DisplayName("Should link habit to goal")
        void shouldLinkHabit() throws Exception {
            GoalHabitLinkRequestDto request = new GoalHabitLinkRequestDto()
                    .habitId(UUID.fromString(habitId));

            mockMvc.perform(post("/api/v1/goals/{goalId}/habits", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.goalId").value(goalId))
                    .andExpect(jsonPath("$.habitId").value(habitId));
        }

        @Test
        @DisplayName("Should return 409 for duplicate link")
        void shouldReturn409ForDuplicate() throws Exception {
            GoalHabitLinkRequestDto request = new GoalHabitLinkRequestDto()
                    .habitId(UUID.fromString(habitId));

            mockMvc.perform(post("/api/v1/goals/{goalId}/habits", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/goals/{goalId}/habits", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 404 for cross-user habit")
        void shouldReturn404ForCrossUser() throws Exception {
            String otherEmail = "cross_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String otherUsername = "cross_" + UUID.randomUUID().toString().substring(0, 8);
            registerUser(otherEmail, otherUsername, "SecurePass1");
            String otherToken = loginAndGetAccessToken(otherEmail, "SecurePass1");

            String otherHabitId = createHabitWithToken(otherToken, "Other's habit");

            GoalHabitLinkRequestDto request = new GoalHabitLinkRequestDto()
                    .habitId(UUID.fromString(otherHabitId));

            mockMvc.perform(post("/api/v1/goals/{goalId}/habits", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UnlinkHabit {

        @Test
        @DisplayName("Should unlink habit from goal")
        void shouldUnlinkHabit() throws Exception {
            linkHabitToGoal(goalId, habitId);

            mockMvc.perform(delete("/api/v1/goals/{goalId}/habits/{habitId}", goalId, habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 for non-linked habit")
        void shouldReturn404ForNonLinked() throws Exception {
            mockMvc.perform(delete("/api/v1/goals/{goalId}/habits/{habitId}", goalId, habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetHabitLinks {

        @Test
        @DisplayName("Should return list of linked habits")
        void shouldReturnLinkedHabits() throws Exception {
            linkHabitToGoal(goalId, habitId);

            mockMvc.perform(get("/api/v1/goals/{goalId}/habits", goalId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].habitId").value(habitId));
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
        return createHabitWithToken(accessToken, title);
    }

    private String createHabitWithToken(String token, String title) throws Exception {
        CreateHabitRequestDto request = new CreateHabitRequestDto()
                .title(title)
                .frequency(CreateHabitRequestDto.FrequencyEnum.DAILY);

        String response = mockMvc.perform(post("/api/v1/habits")
                        .header("Authorization", "Bearer " + token)
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
}
