package ru.zahaand.lifesync.web.habit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.zahaand.lifesync.api.model.*;
import ru.zahaand.lifesync.web.BaseIT;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HabitControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "habit_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "habit_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
    }

    @Nested
    class CreateHabit {

        @Test
        @DisplayName("Should create a DAILY habit")
        void shouldCreateDailyHabit() throws Exception {
            CreateHabitRequestDto request = new CreateHabitRequestDto()
                    .title("Morning run")
                    .description("5km every morning")
                    .frequency(CreateHabitRequestDto.FrequencyEnum.DAILY);

            mockMvc.perform(post("/api/v1/habits")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.title").value("Morning run"))
                    .andExpect(jsonPath("$.frequency").value("DAILY"))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.completedToday").value(false))
                    .andExpect(jsonPath("$.todayLogId").value(nullValue()))
                    .andExpect(jsonPath("$.currentStreak").value(0));
        }

        @Test
        @DisplayName("Should create a CUSTOM habit with target days")
        void shouldCreateCustomHabit() throws Exception {
            CreateHabitRequestDto request = new CreateHabitRequestDto()
                    .title("Gym")
                    .frequency(CreateHabitRequestDto.FrequencyEnum.CUSTOM)
                    .targetDaysOfWeek(List.of(DayOfWeekDto.MONDAY, DayOfWeekDto.WEDNESDAY, DayOfWeekDto.FRIDAY));

            mockMvc.perform(post("/api/v1/habits")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.frequency").value("CUSTOM"))
                    .andExpect(jsonPath("$.targetDaysOfWeek").isArray());
        }

        @Test
        @DisplayName("Should return 401 without token")
        void shouldReturn401WithoutToken() throws Exception {
            CreateHabitRequestDto request = new CreateHabitRequestDto()
                    .title("Test")
                    .frequency(CreateHabitRequestDto.FrequencyEnum.DAILY);

            mockMvc.perform(post("/api/v1/habits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetHabits {

        @Test
        @DisplayName("Should return paginated habit list")
        void shouldReturnPaginatedList() throws Exception {
            createHabit("Habit 1");
            createHabit("Habit 2");

            mockMvc.perform(get("/api/v1/habits")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].completedToday").value(false))
                    .andExpect(jsonPath("$.content[0].currentStreak").value(0));
        }
    }

    @Nested
    class GetHabit {

        @Test
        @DisplayName("Should return habit by id")
        void shouldReturnHabitById() throws Exception {
            String habitId = createHabit("Get me");

            mockMvc.perform(get("/api/v1/habits/{id}", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Get me"))
                    .andExpect(jsonPath("$.completedToday").value(false))
                    .andExpect(jsonPath("$.todayLogId").value(nullValue()))
                    .andExpect(jsonPath("$.currentStreak").value(0));
        }

        @Test
        @DisplayName("Should return 404 for non-existent habit")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/habits/{id}", UUID.randomUUID())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateHabit {

        @Test
        @DisplayName("Should update habit title")
        void shouldUpdateTitle() throws Exception {
            String habitId = createHabit("Old title");

            UpdateHabitRequestDto request = new UpdateHabitRequestDto()
                    .title("New title");

            mockMvc.perform(patch("/api/v1/habits/{id}", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New title"));
        }
    }

    @Nested
    class DeleteHabit {

        @Test
        @DisplayName("Should soft-delete habit")
        void shouldDeleteHabit() throws Exception {
            String habitId = createHabit("Delete me");

            mockMvc.perform(delete("/api/v1/habits/{id}", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/habits/{id}", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CompleteHabit {

        @Test
        @DisplayName("Should log habit completion")
        void shouldLogCompletion() throws Exception {
            String habitId = createHabit("Complete me");

            CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                    .date(LocalDate.now(ZoneOffset.UTC))
                    .note("Done!");

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.habitId").value(habitId))
                    .andExpect(jsonPath("$.note").value("Done!"));
        }

        @Test
        @DisplayName("Should return 409 for duplicate completion")
        void shouldReturn409ForDuplicate() throws Exception {
            String habitId = createHabit("Dup complete");
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                    .date(today);

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should set completedToday=true and todayLogId after completion")
        void shouldSetCompletedTodayAfterCompletion() throws Exception {
            String habitId = createHabit("Enriched habit");

            CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                    .date(LocalDate.now(ZoneOffset.UTC));

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/habits/{id}", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completedToday").value(true))
                    .andExpect(jsonPath("$.todayLogId").value(notNullValue()));
        }

        @Test
        @DisplayName("Should eventually update streak via async consumer after completion")
        void shouldEventuallyUpdateStreakAsync() throws Exception {
            String habitId = createHabit("Async streak habit");

            CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                    .date(LocalDate.now(ZoneOffset.UTC));

            mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            mockMvc.perform(get("/api/v1/habits/{id}/streak", habitId)
                                            .header("Authorization", "Bearer " + accessToken))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.currentStreak").value(1))
                    );
        }
    }

    @Nested
    class GetHabitLogs {

        @Test
        @DisplayName("Should return paginated logs")
        void shouldReturnLogs() throws Exception {
            String habitId = createHabit("Logged habit");
            completeHabit(habitId, LocalDate.now(ZoneOffset.UTC));

            mockMvc.perform(get("/api/v1/habits/{id}/logs", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class DeleteHabitLog {

        @Test
        @DisplayName("Should delete a completion log")
        void shouldDeleteLog() throws Exception {
            String habitId = createHabit("Del log habit");
            String logId = completeHabit(habitId, LocalDate.now(ZoneOffset.UTC));

            mockMvc.perform(delete("/api/v1/habits/{id}/complete/{logId}", habitId, logId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class GetStreak {

        @Test
        @DisplayName("Should return streak data")
        void shouldReturnStreak() throws Exception {
            String habitId = createHabit("Streak habit");

            mockMvc.perform(get("/api/v1/habits/{id}/streak", habitId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStreak").value(0))
                    .andExpect(jsonPath("$.longestStreak").value(0));
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
