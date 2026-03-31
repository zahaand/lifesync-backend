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
import ru.zahaand.lifesync.api.model.GoalProgressUpdateRequestDto;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalProgressIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String goalId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "progress_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "prog_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        goalId = createGoal("Progress test goal");
    }

    @Nested
    class UpdateProgress {

        @Test
        @DisplayName("Should manually update progress to 50")
        void shouldUpdateProgress() throws Exception {
            GoalProgressUpdateRequestDto request = new GoalProgressUpdateRequestDto()
                    .progress(50);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/progress", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.progress").value(50))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("Should set status to COMPLETED when progress reaches 100")
        void shouldSetCompletedAt100() throws Exception {
            GoalProgressUpdateRequestDto request = new GoalProgressUpdateRequestDto()
                    .progress(100);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/progress", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.progress").value(100))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("Should reject progress above 100")
        void shouldRejectProgressAbove100() throws Exception {
            GoalProgressUpdateRequestDto request = new GoalProgressUpdateRequestDto()
                    .progress(150);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/progress", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject negative progress")
        void shouldRejectNegativeProgress() throws Exception {
            GoalProgressUpdateRequestDto request = new GoalProgressUpdateRequestDto()
                    .progress(-1);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/progress", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 for non-existent goal")
        void shouldReturn404ForNonExistent() throws Exception {
            GoalProgressUpdateRequestDto request = new GoalProgressUpdateRequestDto()
                    .progress(50);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/progress", UUID.randomUUID())
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
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
}
