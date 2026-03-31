package ru.zahaand.lifesync.infrastructure.goal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.notification.GoalSentMilestoneRepository;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.util.List;
import java.util.Optional;

@Component
public class GoalNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(GoalNotificationConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-goal-notifier";
    private static final List<Integer> MILESTONES = List.of(25, 50, 75, 100);

    private final GoalRepository goalRepository;
    private final GoalSentMilestoneRepository goalSentMilestoneRepository;
    private final UserRepository userRepository;
    private final TelegramNotificationSender telegramNotificationSender;
    private final ProcessedEventRepository processedEventRepository;
    private final boolean telegramEnabled;

    public GoalNotificationConsumer(GoalRepository goalRepository,
                                    GoalSentMilestoneRepository goalSentMilestoneRepository,
                                    UserRepository userRepository,
                                    TelegramNotificationSender telegramNotificationSender,
                                    ProcessedEventRepository processedEventRepository,
                                    @Value("${lifesync.telegram.enabled:false}") boolean telegramEnabled) {
        this.goalRepository = goalRepository;
        this.goalSentMilestoneRepository = goalSentMilestoneRepository;
        this.userRepository = userRepository;
        this.telegramNotificationSender = telegramNotificationSender;
        this.processedEventRepository = processedEventRepository;
        this.telegramEnabled = telegramEnabled;
    }

    @KafkaListener(topics = "goal.progress.updated", groupId = CONSUMER_GROUP)
    public void consume(ConsumerRecord<String, GoalProgressUpdatedEvent> record) {
        GoalProgressUpdatedEvent event = record.value();
        log.debug("Received event: topic={}, partition={}, offset={}, eventId={}",
                record.topic(), record.partition(), record.offset(), event.eventId());

        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate event {}, skipping", event.eventId());
            return;
        }

        if (!telegramEnabled) {
            log.debug("Telegram disabled, skipping goal notification");
            processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
            return;
        }

        GoalId goalId = new GoalId(event.goalId());
        Optional<Goal> goalOpt = goalRepository.findByIdAndUserId(goalId, event.userId());
        if (goalOpt.isEmpty() || goalOpt.get().isDeleted()) {
            log.warn("Goal not found or deleted: goalId={}", event.goalId());
            processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
            return;
        }

        Goal goal = goalOpt.get();

        User user = userRepository.findById(new UserId(event.userId()))
                .orElse(null);
        if (user == null) {
            log.warn("User not found: userId={}", event.userId());
            processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
            return;
        }

        String chatId = user.getProfile() != null ? user.getProfile().telegramChatId() : null;
        if (chatId == null || chatId.isBlank()) {
            log.debug("No Telegram chatId for userId={}", event.userId());
            processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
            return;
        }

        for (int threshold : MILESTONES) {
            if (event.progressPercentage() >= threshold) {
                if (goalSentMilestoneRepository.existsByGoalIdAndThreshold(goalId, threshold)) {
                    continue;
                }

                String message = buildMessage(goal.getTitle(), threshold);
                try {
                    telegramNotificationSender.send(chatId, message);
                    goalSentMilestoneRepository.save(goalId, threshold);
                    log.info("Goal milestone notification sent: goalId={}, threshold={}%",
                            event.goalId(), threshold);
                } catch (Exception e) {
                    log.warn("Failed to send goal milestone notification: goalId={}, threshold={}, error={}",
                            event.goalId(), threshold, e.getMessage());
                }
            }
        }

        processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
        log.info("Goal milestone notifications processed: goalId={}, progress={}",
                event.goalId(), event.progressPercentage());
    }

    private String buildMessage(String goalTitle, int threshold) {
        if (threshold == 100) {
            return "\uD83C\uDFAF " + goalTitle + ": Goal achieved! Congratulations! \uD83C\uDF89";
        }
        return "\uD83C\uDFAF " + goalTitle + ": " + threshold + "% complete! Keep going!";
    }
}
