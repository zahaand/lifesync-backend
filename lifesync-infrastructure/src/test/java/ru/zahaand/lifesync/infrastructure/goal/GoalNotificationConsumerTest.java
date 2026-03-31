package ru.zahaand.lifesync.infrastructure.goal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;
import ru.zahaand.lifesync.domain.notification.GoalSentMilestoneRepository;
import ru.zahaand.lifesync.domain.user.Role;
import ru.zahaand.lifesync.domain.user.TelegramNotificationSender;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserProfile;
import ru.zahaand.lifesync.domain.user.UserRepository;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalNotificationConsumerTest {

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private GoalSentMilestoneRepository goalSentMilestoneRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TelegramNotificationSender telegramNotificationSender;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private GoalNotificationConsumer consumer;

    private static final UUID GOAL_UUID = UUID.randomUUID();
    private static final GoalId GOAL_ID = new GoalId(GOAL_UUID);
    private static final UUID USER_UUID = UUID.randomUUID();
    private static final UserId USER_ID = new UserId(USER_UUID);
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String CHAT_ID = "123456789";

    @BeforeEach
    void setUp() {
        consumer = new GoalNotificationConsumer(goalRepository, goalSentMilestoneRepository,
                userRepository, telegramNotificationSender, processedEventRepository, true);
    }

    private GoalProgressUpdatedEvent event(int progress) {
        return new GoalProgressUpdatedEvent(EVENT_ID, GOAL_UUID, USER_UUID, null, progress, Instant.now());
    }

    private ConsumerRecord<String, GoalProgressUpdatedEvent> record(GoalProgressUpdatedEvent event) {
        return new ConsumerRecord<>("goal.progress.updated", 0, 0L, GOAL_UUID.toString(), event);
    }

    private Goal goal(String title) {
        return new Goal(GOAL_ID, USER_UUID, title, null, null,
                GoalStatus.ACTIVE, 0, Instant.now(), Instant.now(), null);
    }

    private User userWithTelegram(String chatId) {
        UserProfile profile = new UserProfile(null, null, null, chatId);
        return new User(USER_ID, "test@test.com", "test", "hash",
                Role.USER, true, Instant.now(), Instant.now(), null, profile);
    }

    private void stubGoalAndUser() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_UUID))
                .thenReturn(Optional.of(goal("Run a marathon")));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(CHAT_ID)));
    }

    @Nested
    class Consume {

        @Test
        @DisplayName("Should send milestone at 25 percent")
        void shouldSendMilestoneAt25Percent() {
            GoalProgressUpdatedEvent evt = event(25);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            stubGoalAndUser();
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(GOAL_ID, 25)).thenReturn(false);

            consumer.consume(record(evt));

            verify(telegramNotificationSender).send(CHAT_ID,
                    "\uD83C\uDFAF Run a marathon: 25% complete! Keep going!");
            verify(goalSentMilestoneRepository).save(GOAL_ID, 25);
            verify(processedEventRepository).save(EVENT_ID, "GoalProgressUpdatedEvent", "lifesync-goal-notifier");
        }

        @Test
        @DisplayName("Should send milestone at 100 percent with special message")
        void shouldSendMilestoneAt100PercentWithSpecialMessage() {
            GoalProgressUpdatedEvent evt = event(100);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            stubGoalAndUser();
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(any(), anyInt())).thenReturn(false);

            consumer.consume(record(evt));

            verify(telegramNotificationSender).send(CHAT_ID,
                    "\uD83C\uDFAF Run a marathon: Goal achieved! Congratulations! \uD83C\uDF89");
        }

        @Test
        @DisplayName("Should send multiple milestones on jump")
        void shouldSendMultipleMilestonesOnJump() {
            GoalProgressUpdatedEvent evt = event(80);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            stubGoalAndUser();
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(any(), anyInt())).thenReturn(false);

            consumer.consume(record(evt));

            verify(telegramNotificationSender, times(3)).send(eq(CHAT_ID), anyString());
            verify(goalSentMilestoneRepository).save(GOAL_ID, 25);
            verify(goalSentMilestoneRepository).save(GOAL_ID, 50);
            verify(goalSentMilestoneRepository).save(GOAL_ID, 75);
        }

        @Test
        @DisplayName("Should skip already sent milestone")
        void shouldSkipAlreadySentMilestone() {
            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            stubGoalAndUser();
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(GOAL_ID, 25)).thenReturn(true);
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(GOAL_ID, 50)).thenReturn(false);

            consumer.consume(record(evt));

            verify(telegramNotificationSender, times(1)).send(eq(CHAT_ID), anyString());
            verify(goalSentMilestoneRepository, never()).save(GOAL_ID, 25);
            verify(goalSentMilestoneRepository).save(GOAL_ID, 50);
        }

        @Test
        @DisplayName("Should skip when Telegram disabled")
        void shouldSkipWhenTelegramDisabled() {
            consumer = new GoalNotificationConsumer(goalRepository, goalSentMilestoneRepository,
                    userRepository, telegramNotificationSender, processedEventRepository, false);

            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);

            consumer.consume(record(evt));

            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository).save(EVENT_ID, "GoalProgressUpdatedEvent", "lifesync-goal-notifier");
        }

        @Test
        @DisplayName("Should skip when no Telegram chat ID")
        void shouldSkipWhenNoTelegramChatId() {
            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_UUID))
                    .thenReturn(Optional.of(goal("Run a marathon")));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(null)));

            consumer.consume(record(evt));

            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository).save(EVENT_ID, "GoalProgressUpdatedEvent", "lifesync-goal-notifier");
        }

        @Test
        @DisplayName("Should skip deleted goal")
        void shouldSkipDeletedGoal() {
            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            Goal deletedGoal = new Goal(GOAL_ID, USER_UUID, "Deleted", null, null,
                    GoalStatus.ACTIVE, 0, Instant.now(), Instant.now(), Instant.now());
            when(goalRepository.findByIdAndUserId(GOAL_ID, USER_UUID)).thenReturn(Optional.of(deletedGoal));

            consumer.consume(record(evt));

            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository).save(EVENT_ID, "GoalProgressUpdatedEvent", "lifesync-goal-notifier");
        }

        @Test
        @DisplayName("Should not save milestone on send failure")
        void shouldNotSaveMilestoneOnSendFailure() {
            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(false);
            stubGoalAndUser();
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(GOAL_ID, 25)).thenReturn(false);
            when(goalSentMilestoneRepository.existsByGoalIdAndThreshold(GOAL_ID, 50)).thenReturn(false);

            doThrow(new RuntimeException("Telegram API error"))
                    .when(telegramNotificationSender).send(any(), any());

            consumer.consume(record(evt));

            verify(goalSentMilestoneRepository, never()).save(any(), anyInt());
        }

        @Test
        @DisplayName("Should handle duplicate event")
        void shouldHandleDuplicateEvent() {
            GoalProgressUpdatedEvent evt = event(50);
            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-goal-notifier"))
                    .thenReturn(true);

            consumer.consume(record(evt));

            verify(goalRepository, never()).findByIdAndUserId(any(), any());
            verify(telegramNotificationSender, never()).send(any(), any());
        }
    }
}
