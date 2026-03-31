package ru.zahaand.lifesync.infrastructure.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.application.habit.StreakCalculatorService;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class TelegramNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-telegram-notifier";
    private static final Set<Integer> MILESTONES = Set.of(7, 14, 21, 30, 60, 90);

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final StreakCalculatorService streakCalculatorService;
    private final UserRepository userRepository;
    private final TelegramNotificationSender telegramNotificationSender;
    private final ProcessedEventRepository processedEventRepository;

    public TelegramNotificationConsumer(HabitRepository habitRepository,
                                         HabitLogRepository habitLogRepository,
                                         StreakCalculatorService streakCalculatorService,
                                         UserRepository userRepository,
                                         TelegramNotificationSender telegramNotificationSender,
                                         ProcessedEventRepository processedEventRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.streakCalculatorService = streakCalculatorService;
        this.userRepository = userRepository;
        this.telegramNotificationSender = telegramNotificationSender;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "habit.log.completed", groupId = CONSUMER_GROUP)
    public void consume(ConsumerRecord<String, HabitCompletedEvent> record) {
        HabitCompletedEvent event = record.value();
        log.debug("Received event: topic={}, partition={}, offset={}, eventId={}",
                record.topic(), record.partition(), record.offset(), event.eventId());

        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate event {}, skipping", event.eventId());
            return;
        }

        HabitId habitId = new HabitId(event.habitId());
        Habit habit = habitRepository.findByIdAndUserId(habitId, event.userId())
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + event.habitId()));

        List<LocalDate> logDates = habitLogRepository.findLogDatesDesc(habitId, event.userId());
        HabitStreak streak = streakCalculatorService.calculate(
                habitId, habit.getFrequency(), habit.getTargetDaysOfWeek(), logDates);

        if (!MILESTONES.contains(streak.currentStreak())) {
            log.debug("No milestone: habitId={}, streak={}", event.habitId(), streak.currentStreak());
            processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);
            return;
        }

        User user = userRepository.findById(new UserId(event.userId()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + event.userId()));

        String chatId = user.getProfile() != null ? user.getProfile().telegramChatId() : null;
        if (chatId == null || chatId.isBlank()) {
            log.debug("No Telegram for userId={}", event.userId());
            processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);
            return;
        }

        String message = "You've reached a " + streak.currentStreak() + "-day streak! Keep going!";
        telegramNotificationSender.send(chatId, message);

        processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);

        log.info("Telegram milestone notification sent: userId={}, streak={}",
                event.userId(), streak.currentStreak());
    }
}
