package ru.zahaand.lifesync.infrastructure.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.application.habit.StreakCalculatorService;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.user.*;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationConsumerTest {

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private StreakCalculatorService streakCalculatorService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TelegramNotificationSender telegramNotificationSender;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private TelegramNotificationConsumer consumer;

    private static final UUID HABIT_UUID = UUID.randomUUID();
    private static final HabitId HABIT_ID = new HabitId(HABIT_UUID);
    private static final UUID USER_UUID = UUID.randomUUID();
    private static final UserId USER_ID = new UserId(USER_UUID);
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final LocalDate LOG_DATE = LocalDate.of(2026, 3, 30);
    private static final String CHAT_ID = "123456789";

    @BeforeEach
    void setUp() {
        consumer = new TelegramNotificationConsumer(habitRepository, habitLogRepository,
                streakCalculatorService, userRepository, telegramNotificationSender,
                processedEventRepository);
    }

    private HabitCompletedEvent event() {
        return new HabitCompletedEvent(EVENT_ID, HABIT_UUID, USER_UUID, LOG_DATE,
                UUID.randomUUID(), Instant.now());
    }

    private ConsumerRecord<String, HabitCompletedEvent> record(HabitCompletedEvent event) {
        return new ConsumerRecord<>("habit.log.completed", 0, 0L, HABIT_UUID.toString(), event);
    }

    private Habit habit() {
        return new Habit(HABIT_ID, USER_UUID, "Test", null, Frequency.DAILY,
                null, null, true, Instant.now(), Instant.now(), null);
    }

    private User userWithTelegram(String chatId) {
        UserProfile profile = new UserProfile(null, null, null, chatId);
        return new User(USER_ID, "test@test.com", "test", "hash",
                Role.USER, true, Instant.now(), Instant.now(), null, profile);
    }

    private void stubHabitAndStreak(int currentStreak) {
        Habit h = habit();
        when(habitRepository.findByIdAndUserId(HABIT_ID, USER_UUID)).thenReturn(Optional.of(h));
        when(habitLogRepository.findLogDatesDesc(HABIT_ID, USER_UUID)).thenReturn(List.of(LOG_DATE));
        when(streakCalculatorService.calculate(eq(HABIT_ID), eq(Frequency.DAILY), any(), eq(List.of(LOG_DATE))))
                .thenReturn(new HabitStreak(HABIT_ID, currentStreak, currentStreak, LOG_DATE));
    }

    @Nested
    class Consume {

        @ParameterizedTest
        @MethodSource("ru.zahaand.lifesync.infrastructure.notification.TelegramNotificationConsumerTest#milestoneValues")
        @DisplayName("Should send Telegram notification for milestone streak")
        void shouldSendNotificationForMilestone(int milestone) {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(false);
            stubHabitAndStreak(milestone);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(CHAT_ID)));

            consumer.consume(record(evt));

            String expectedMessage = "\uD83D\uDD25 " + "Test" + ": " + milestone + "-day streak! Keep going!";
            verify(telegramNotificationSender).send(CHAT_ID, expectedMessage);
            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-telegram-notifier");
        }

        @Test
        @DisplayName("Should not send when streak is not a milestone")
        void shouldNotSendWhenNoMilestone() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(false);
            stubHabitAndStreak(6);

            consumer.consume(record(evt));

            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-telegram-notifier");
        }

        @Test
        @DisplayName("Should not send when user has no Telegram chat ID")
        void shouldNotSendWhenNoTelegramChatId() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(false);
            stubHabitAndStreak(7);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(null)));

            consumer.consume(record(evt));

            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository).save(EVENT_ID, "HabitCompletedEvent", "lifesync-telegram-notifier");
        }

        @Test
        @DisplayName("Should skip duplicate event")
        void shouldSkipDuplicateEvent() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(true);

            consumer.consume(record(evt));

            verify(habitRepository, never()).findByIdAndUserId(any(), any());
            verify(telegramNotificationSender, never()).send(any(), any());
            verify(processedEventRepository, never()).save(any(), any(), any());
        }

        @Test
        @DisplayName("Should include habit title in milestone message")
        void shouldIncludeHabitTitleInMessage() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(false);
            stubHabitAndStreak(7);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(CHAT_ID)));

            consumer.consume(record(evt));

            verify(telegramNotificationSender).send(eq(CHAT_ID),
                    org.mockito.ArgumentMatchers.contains("Test"));
        }

        @Test
        @DisplayName("Should propagate exception when Telegram adapter throws")
        void shouldPropagateWhenTelegramAdapterThrows() {
            HabitCompletedEvent evt = event();

            when(processedEventRepository.existsByEventIdAndConsumerGroup(EVENT_ID, "lifesync-telegram-notifier"))
                    .thenReturn(false);
            stubHabitAndStreak(7);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTelegram(CHAT_ID)));
            doThrow(new RuntimeException("Telegram API error"))
                    .when(telegramNotificationSender).send(any(), any());

            assertThrows(RuntimeException.class, () -> consumer.consume(record(evt)));
        }
    }

    static Stream<Integer> milestoneValues() {
        return Stream.of(7, 14, 21, 30, 60, 90);
    }
}
