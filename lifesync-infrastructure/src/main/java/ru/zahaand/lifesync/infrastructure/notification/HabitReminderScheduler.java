package ru.zahaand.lifesync.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitWithUser;
import ru.zahaand.lifesync.domain.notification.SentReminderRepository;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class HabitReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(HabitReminderScheduler.class);

    private final HabitRepository habitRepository;
    private final SentReminderRepository sentReminderRepository;
    private final HabitLogRepository habitLogRepository;
    private final TelegramNotificationSender telegramNotificationSender;
    private final Clock clock;
    private final boolean telegramEnabled;

    public HabitReminderScheduler(HabitRepository habitRepository,
                                  SentReminderRepository sentReminderRepository,
                                  HabitLogRepository habitLogRepository,
                                  TelegramNotificationSender telegramNotificationSender,
                                  Clock clock,
                                  @Value("${lifesync.telegram.enabled:false}") boolean telegramEnabled) {
        this.habitRepository = habitRepository;
        this.sentReminderRepository = sentReminderRepository;
        this.habitLogRepository = habitLogRepository;
        this.telegramNotificationSender = telegramNotificationSender;
        this.clock = clock;
        this.telegramEnabled = telegramEnabled;
    }

    @Scheduled(cron = "0 * * * * *")
    public void sendReminders() {
        if (!telegramEnabled) {
            return;
        }

        List<HabitWithUser> habits = habitRepository.findAllActiveWithReminderTime();
        log.debug("Loaded {} habits with reminder time", habits.size());

        int sentCount = 0;
        for (HabitWithUser habitWithUser : habits) {
            try {
                if (processSingleHabit(habitWithUser)) {
                    sentCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to send reminder: habitId={}, error={}",
                        habitWithUser.habit().getId().value(), e.getMessage());
            }
        }

        log.info("Reminders sent: {} of {} habits processed", sentCount, habits.size());
    }

    private boolean processSingleHabit(HabitWithUser habitWithUser) {
        var habit = habitWithUser.habit();
        var habitId = habit.getId();
        var userId = habit.getUserId();

        ZoneId zoneId = parseTimezone(habitWithUser.timezone());
        LocalTime localTime = LocalTime.now(clock.withZone(zoneId));

        if (localTime.getHour() != habit.getReminderTime().getHour()
                || localTime.getMinute() != habit.getReminderTime().getMinute()) {
            return false;
        }

        LocalDate userLocalDate = LocalDate.now(clock.withZone(zoneId));

        if (sentReminderRepository.existsByHabitIdAndDate(habitId, userLocalDate)) {
            log.debug("Reminder already sent today: habitId={}", habitId.value());
            return false;
        }

        String chatId = habitWithUser.telegramChatId();
        if (chatId == null || chatId.isBlank()) {
            log.debug("No Telegram chatId for habitId={}", habitId.value());
            return false;
        }

        if (habitLogRepository.findByHabitIdAndLogDateAndUserId(habitId, userLocalDate, userId).isPresent()) {
            log.debug("Habit already completed today: habitId={}", habitId.value());
            return false;
        }

        String message = "\u23F0 Time to " + habit.getTitle() + "! Don't break your streak!";
        telegramNotificationSender.send(chatId, message);
        sentReminderRepository.save(habitId, userId, userLocalDate);

        log.info("Reminder sent: habitId={}, userId={}", habitId.value(), userId);
        return true;
    }

    private ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to UTC", timezone);
            return ZoneId.of("UTC");
        }
    }
}
