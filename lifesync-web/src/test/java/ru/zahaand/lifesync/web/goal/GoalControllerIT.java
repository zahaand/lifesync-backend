package ru.zahaand.lifesync.web.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.GoalCreateRequestDto;
import ru.zahaand.lifesync.api.model.GoalUpdateRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "goal_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "goal_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
    }

    @Nested
    class CreateGoal {

        @Test
        @DisplayName("Should create a goal with ACTIVE status and 0 progress")
        void shouldCreateGoal() throws Exception {
            GoalCreateRequestDto request = new GoalCreateRequestDto()
                    .title("Learn Java 21")
                    .description("Master new features")
                    .targetDate(LocalDate.of(2026, 12, 31));

            mockMvc.perform(post("/api/v1/goals")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.title").value("Learn Java 21"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.progress").value(0));
        }

        @Test
        @DisplayName("Should create goal without target date")
        void shouldCreateWithoutTargetDate() throws Exception {
            GoalCreateRequestDto request = new GoalCreateRequestDto()
                    .title("Open-ended goal");

            mockMvc.perform(post("/api/v1/goals")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.targetDate").isEmpty());
        }

        @Test
        @DisplayName("Should return 401 without token")
        void shouldReturn401WithoutToken() throws Exception {
            GoalCreateRequestDto request = new GoalCreateRequestDto()
                    .title("Test");

            mockMvc.perform(post("/api/v1/goals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetGoals {

        @Test
        @DisplayName("Should return paginated goal list")
        void shouldReturnPaginatedList() throws Exception {
            createGoal("Goal 1");
            createGoal("Goal 2");

            mockMvc.perform(get("/api/v1/goals")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() throws Exception {
            createGoal("Active goal");

            mockMvc.perform(get("/api/v1/goals")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("status", "COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    @Nested
    class GetGoal {

        @Test
        @DisplayName("Should return goal detail with milestones and linkedHabitIds")
        void shouldReturnGoalDetail() throws Exception {
            String goalId = createGoal("Detail goal");

            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Detail goal"))
                    .andExpect(jsonPath("$.milestones").isArray())
                    .andExpect(jsonPath("$.linkedHabitIds").isArray());
        }

        @Test
        @DisplayName("Should return 404 for non-existent goal")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/goals/{goalId}", UUID.randomUUID())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should isolate goals by user ownership")
        void shouldIsolateByOwnership() throws Exception {
            String goalId = createGoal("Owner's goal");

            String otherEmail = "other_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String otherUsername = "other_" + UUID.randomUUID().toString().substring(0, 8);
            registerUser(otherEmail, otherUsername, "SecurePass1");
            String otherToken = loginAndGetAccessToken(otherEmail, "SecurePass1");

            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateGoal {

        @Test
        @DisplayName("Should update goal title")
        void shouldUpdateTitle() throws Exception {
            String goalId = createGoal("Old title");

            GoalUpdateRequestDto request = new GoalUpdateRequestDto()
                    .title("New title");

            mockMvc.perform(patch("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New title"));
        }

        @Test
        @DisplayName("Should update goal status to COMPLETED")
        void shouldUpdateStatus() throws Exception {
            String goalId = createGoal("Complete me");

            GoalUpdateRequestDto request = new GoalUpdateRequestDto()
                    .status(GoalUpdateRequestDto.StatusEnum.COMPLETED);

            mockMvc.perform(patch("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    @Nested
    class DeleteGoal {

        @Test
        @DisplayName("Should soft-delete goal")
        void shouldDeleteGoal() throws Exception {
            String goalId = createGoal("Delete me");

            mockMvc.perform(delete("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());
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

    protected String createGoal(String title) throws Exception {
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
}
