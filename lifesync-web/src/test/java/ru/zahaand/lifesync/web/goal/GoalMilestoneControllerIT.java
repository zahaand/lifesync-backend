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
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.MilestoneCreateRequestDto;
import ru.zahaand.lifesync.api.model.MilestoneUpdateRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.web.BaseIT;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalMilestoneControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String goalId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "milestone_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "mile_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
        goalId = createGoal("Milestone test goal");
    }

    @Nested
    class AddMilestone {

        @Test
        @DisplayName("Should add milestone to goal")
        void shouldAddMilestone() throws Exception {
            MilestoneCreateRequestDto request = new MilestoneCreateRequestDto()
                    .title("Step 1")
                    .sortOrder(1);

            mockMvc.perform(post("/api/v1/goals/{goalId}/milestones", goalId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.title").value("Step 1"))
                    .andExpect(jsonPath("$.sortOrder").value(1))
                    .andExpect(jsonPath("$.completed").value(false));
        }

        @Test
        @DisplayName("Should return 404 for non-existent goal")
        void shouldReturn404ForNonExistentGoal() throws Exception {
            MilestoneCreateRequestDto request = new MilestoneCreateRequestDto()
                    .title("Step 1");

            mockMvc.perform(post("/api/v1/goals/{goalId}/milestones", UUID.randomUUID())
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateMilestone {

        @Test
        @DisplayName("Should mark milestone as completed")
        void shouldMarkCompleted() throws Exception {
            String milestoneId = addMilestone(goalId, "Complete me");

            MilestoneUpdateRequestDto request = new MilestoneUpdateRequestDto()
                    .completed(true);

            mockMvc.perform(patch("/api/v1/goals/{goalId}/milestones/{milestoneId}", goalId, milestoneId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(true))
                    .andExpect(jsonPath("$.completedAt").value(notNullValue()));
        }

        @Test
        @DisplayName("Should uncomplete milestone and clear completedAt")
        void shouldUncomplete() throws Exception {
            String milestoneId = addMilestone(goalId, "Toggle me");

            MilestoneUpdateRequestDto completeReq = new MilestoneUpdateRequestDto().completed(true);
            mockMvc.perform(patch("/api/v1/goals/{goalId}/milestones/{milestoneId}", goalId, milestoneId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(completeReq)))
                    .andExpect(status().isOk());

            MilestoneUpdateRequestDto uncompleteReq = new MilestoneUpdateRequestDto().completed(false);
            mockMvc.perform(patch("/api/v1/goals/{goalId}/milestones/{milestoneId}", goalId, milestoneId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(uncompleteReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(false))
                    .andExpect(jsonPath("$.completedAt").isEmpty());
        }

        @Test
        @DisplayName("Should update milestone title")
        void shouldUpdateTitle() throws Exception {
            String milestoneId = addMilestone(goalId, "Old title");

            MilestoneUpdateRequestDto request = new MilestoneUpdateRequestDto()
                    .title("New title");

            mockMvc.perform(patch("/api/v1/goals/{goalId}/milestones/{milestoneId}", goalId, milestoneId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New title"));
        }
    }

    @Nested
    class DeleteMilestone {

        @Test
        @DisplayName("Should soft-delete milestone")
        void shouldDeleteMilestone() throws Exception {
            String milestoneId = addMilestone(goalId, "Delete me");

            mockMvc.perform(delete("/api/v1/goals/{goalId}/milestones/{milestoneId}", goalId, milestoneId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class GoalOwnershipCheck {

        @Test
        @DisplayName("Should return 404 when accessing another user's goal milestones")
        void shouldReturn404ForOtherUsersGoal() throws Exception {
            String otherEmail = "other_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String otherUsername = "oth_" + UUID.randomUUID().toString().substring(0, 8);
            registerUser(otherEmail, otherUsername, "SecurePass1");
            String otherToken = loginAndGetAccessToken(otherEmail, "SecurePass1");

            MilestoneCreateRequestDto request = new MilestoneCreateRequestDto()
                    .title("Cross-user");

            mockMvc.perform(post("/api/v1/goals/{goalId}/milestones", goalId)
                            .header("Authorization", "Bearer " + otherToken)
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

    private String addMilestone(String goalId, String title) throws Exception {
        MilestoneCreateRequestDto request = new MilestoneCreateRequestDto()
                .title(title);

        String response = mockMvc.perform(post("/api/v1/goals/{goalId}/milestones", goalId)
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
