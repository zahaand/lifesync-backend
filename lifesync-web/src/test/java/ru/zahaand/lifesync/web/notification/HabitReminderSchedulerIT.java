package ru.zahaand.lifesync.web.notification;

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
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.notification.SentReminderRepository;
import ru.zahaand.lifesync.infrastructure.notification.HabitReminderScheduler;
import ru.zahaand.lifesync.web.BaseIT;
import ru.zahaand.lifesync.web.TestTelegramNotificationSender;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("HabitReminderScheduler integration tests")
class HabitReminderSchedulerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private SentReminderRepository sentReminderRepository;

    @Autowired
    private HabitLogRepository habitLogRepository;

    @Autowired
    private TestTelegramNotificationSender testTelegramNotificationSender;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        testTelegramNotificationSender.clear();
        String email = "sched_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String username = "sched_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(email, username, "SecurePass1");
        accessToken = loginAndGetAccessToken(email, "SecurePass1");
    }

    @Nested
    class SendReminders {

        @Test
        @DisplayName("Should create sent_reminders row when reminder time matches")
        void shouldCreateSentReminderRowWhenTimeMatches() throws Exception {
            String habitId = createHabitWithReminder("Test habit", "08:00");
            setTelegramChatId("123456789");
            setUserTimezone("UTC");

            ZonedDateTime zdt = ZonedDateTime.of(2026, 3, 31, 8, 0, 0, 0, ZoneId.of("UTC"));
            Clock testClock = Clock.fixed(zdt.toInstant(), ZoneId.of("UTC"));

            HabitReminderScheduler scheduler = new HabitReminderScheduler(
                    habitRepository, sentReminderRepository, habitLogRepository,
                    testTelegramNotificationSender, testClock, true);

            scheduler.sendReminders();

            HabitId hId = new HabitId(UUID.fromString(habitId));
            assertTrue(sentReminderRepository.existsByHabitIdAndDate(hId, LocalDate.of(2026, 3, 31)));
        }

        @Test
        @DisplayName("Should not create duplicate sent_reminders row on second run")
        void shouldNotCreateDuplicateRow() throws Exception {
            String habitId = createHabitWithReminder("Dup test", "08:00");
            setTelegramChatId("123456789");
            setUserTimezone("UTC");

            ZonedDateTime zdt = ZonedDateTime.of(2026, 3, 31, 8, 0, 0, 0, ZoneId.of("UTC"));
            Clock testClock = Clock.fixed(zdt.toInstant(), ZoneId.of("UTC"));

            HabitReminderScheduler scheduler = new HabitReminderScheduler(
                    habitRepository, sentReminderRepository, habitLogRepository,
                    testTelegramNotificationSender, testClock, true);

            scheduler.sendReminders();
            scheduler.sendReminders();

            HabitId hId = new HabitId(UUID.fromString(habitId));
            assertTrue(sentReminderRepository.existsByHabitIdAndDate(hId, LocalDate.of(2026, 3, 31)));
        }

        @Test
        @DisplayName("Should skip reminder when habit already completed today")
        void shouldSkipWhenHabitCompletedToday() throws Exception {
            String habitId = createHabitWithReminder("Completed test", "08:00");
            setTelegramChatId("123456789");
            setUserTimezone("UTC");

            completeHabit(habitId, LocalDate.of(2026, 3, 31));

            ZonedDateTime zdt = ZonedDateTime.of(2026, 3, 31, 8, 0, 0, 0, ZoneId.of("UTC"));
            Clock testClock = Clock.fixed(zdt.toInstant(), ZoneId.of("UTC"));

            HabitReminderScheduler scheduler = new HabitReminderScheduler(
                    habitRepository, sentReminderRepository, habitLogRepository,
                    testTelegramNotificationSender, testClock, true);

            scheduler.sendReminders();

            HabitId hId = new HabitId(UUID.fromString(habitId));
            assertFalse(sentReminderRepository.existsByHabitIdAndDate(hId, LocalDate.of(2026, 3, 31)));
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

    private String createHabitWithReminder(String title, String reminderTime) throws Exception {
        CreateHabitRequestDto request = new CreateHabitRequestDto()
                .title(title)
                .frequency(CreateHabitRequestDto.FrequencyEnum.DAILY)
                .reminderTime(reminderTime);

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

    private void setTelegramChatId(String chatId) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("telegramChatId", chatId));
        mockMvc.perform(put("/api/v1/users/me/telegram")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void setUserTimezone(String timezone) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("timezone", timezone));
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void completeHabit(String habitId, LocalDate date) throws Exception {
        CompleteHabitRequestDto request = new CompleteHabitRequestDto()
                .date(date);

        mockMvc.perform(post("/api/v1/habits/{id}/complete", habitId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
