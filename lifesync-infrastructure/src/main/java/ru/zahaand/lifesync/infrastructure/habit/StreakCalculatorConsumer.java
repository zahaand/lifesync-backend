package ru.zahaand.lifesync.infrastructure.habit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.application.habit.StreakCalculatorService;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class StreakCalculatorConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreakCalculatorConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-streak-calculator";

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitStreakRepository habitStreakRepository;
    private final StreakCalculatorService streakCalculatorService;
    private final ProcessedEventRepository processedEventRepository;

    public StreakCalculatorConsumer(HabitRepository habitRepository,
                                    HabitLogRepository habitLogRepository,
                                    HabitStreakRepository habitStreakRepository,
                                    StreakCalculatorService streakCalculatorService,
                                    ProcessedEventRepository processedEventRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.habitStreakRepository = habitStreakRepository;
        this.streakCalculatorService = streakCalculatorService;
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
        HabitStreak newStreak = streakCalculatorService.calculate(
                habitId, habit.getFrequency(), habit.getTargetDaysOfWeek(), logDates);

        Optional<HabitStreak> existing = habitStreakRepository.findByHabitIdAndUserId(habitId, event.userId());
        if (existing.isPresent()) {
            habitStreakRepository.update(newStreak);
        } else {
            habitStreakRepository.save(newStreak);
        }

        processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);

        log.info("Streak recalculated: habitId={}, currentStreak={}, longestStreak={}",
                event.habitId(), newStreak.currentStreak(), newStreak.longestStreak());
    }
}
