package ru.zahaand.lifesync.infrastructure.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLog;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitWithUser;
import ru.zahaand.lifesync.domain.notification.SentReminderRepository;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitReminderSchedulerTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private SentReminderRepository sentReminderRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private TelegramNotificationSender telegramNotificationSender;

    private static final UUID USER_UUID = UUID.randomUUID();
    private static final UUID HABIT_UUID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(HABIT_UUID);
    private static final String CHAT_ID = "123456789";

    private HabitReminderScheduler scheduler;

    private Clock fixedClock(int hour, int minute, String zoneId) {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 3, 31, hour, minute, 0, 0, ZoneId.of(zoneId));
        return Clock.fixed(zdt.toInstant(), ZoneId.of("UTC"));
    }

    private Habit habit(LocalTime reminderTime) {
        return new Habit(HABIT_ID, USER_UUID, "Morning run", null,
                Frequency.DAILY, null, reminderTime, true,
                Instant.now(), Instant.now(), null);
    }

    private HabitWithUser habitWithUser(LocalTime reminderTime, String chatId, String timezone) {
        return new HabitWithUser(habit(reminderTime), chatId, timezone);
    }

    @Nested
    class SendReminders {

        @Test
        @DisplayName("Should send reminder when time matches")
        void shouldSendReminderWhenTimeMatches() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(false);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID,
                    LocalDate.of(2026, 3, 31), USER_UUID)).thenReturn(Optional.empty());

            scheduler.sendReminders();

            verify(telegramNotificationSender).send(eq(CHAT_ID),
                    eq("\u23F0 Time to Morning run! Don't break your streak!"));
            verify(sentReminderRepository).save(HABIT_ID, USER_UUID, LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("Should skip when Telegram is disabled")
        void shouldSkipWhenTelegramDisabled() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, false);

            scheduler.sendReminders();

            verify(habitRepository, never()).findAllActiveWithReminderTime();
            verify(telegramNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("Should skip when already sent today")
        void shouldSkipWhenAlreadySentToday() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(true);

            scheduler.sendReminders();

            verify(telegramNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("Should skip when habit completed today")
        void shouldSkipWhenHabitCompletedToday() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(false);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID,
                    LocalDate.of(2026, 3, 31), USER_UUID))
                    .thenReturn(Optional.of(new HabitLog(
                            new ru.zahaand.lifesync.domain.habit.HabitLogId(UUID.randomUUID()),
                            HABIT_ID, USER_UUID, LocalDate.of(2026, 3, 31), null,
                            Instant.now(), Instant.now(), null)));

            scheduler.sendReminders();

            verify(telegramNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("Should skip when no Telegram chat ID")
        void shouldSkipWhenNoTelegramChatId() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), null, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(false);

            scheduler.sendReminders();

            verify(telegramNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("Should skip when time does not match")
        void shouldSkipWhenTimeDoesNotMatch() {
            Clock clock = fixedClock(9, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));

            scheduler.sendReminders();

            verify(telegramNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("Should handle invalid timezone with UTC fallback")
        void shouldHandleInvalidTimezoneWithUtcFallback() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "Invalid/Zone");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(false);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID,
                    LocalDate.of(2026, 3, 31), USER_UUID)).thenReturn(Optional.empty());

            scheduler.sendReminders();

            verify(telegramNotificationSender).send(eq(CHAT_ID), any());
            verify(sentReminderRepository).save(HABIT_ID, USER_UUID, LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("Should continue on Telegram failure")
        void shouldContinueOnTelegramFailure() {
            Clock clock = fixedClock(8, 0, "UTC");
            scheduler = new HabitReminderScheduler(habitRepository, sentReminderRepository,
                    habitLogRepository, telegramNotificationSender, clock, true);

            HabitWithUser hwu = habitWithUser(LocalTime.of(8, 0), CHAT_ID, "UTC");
            when(habitRepository.findAllActiveWithReminderTime()).thenReturn(List.of(hwu));
            when(sentReminderRepository.existsByHabitIdAndDate(HABIT_ID, LocalDate.of(2026, 3, 31)))
                    .thenReturn(false);
            when(habitLogRepository.findByHabitIdAndLogDateAndUserId(HABIT_ID,
                    LocalDate.of(2026, 3, 31), USER_UUID)).thenReturn(Optional.empty());
            doThrow(new RuntimeException("Telegram API error"))
                    .when(telegramNotificationSender).send(any(), any());

            scheduler.sendReminders();

            verify(sentReminderRepository, never()).save(any(), any(), any());
        }
    }
}
