package ru.zahaand.lifesync.infrastructure.goal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.application.goal.RecalculateGoalProgressUseCase;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

import java.util.List;

@Component
public class GoalProgressConsumer {

    private static final Logger log = LoggerFactory.getLogger(GoalProgressConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-goal-progress";

    private final GoalHabitLinkRepository goalHabitLinkRepository;
    private final RecalculateGoalProgressUseCase recalculateGoalProgressUseCase;
    private final ProcessedEventRepository processedEventRepository;

    public GoalProgressConsumer(GoalHabitLinkRepository goalHabitLinkRepository,
                                RecalculateGoalProgressUseCase recalculateGoalProgressUseCase,
                                ProcessedEventRepository processedEventRepository) {
        this.goalHabitLinkRepository = goalHabitLinkRepository;
        this.recalculateGoalProgressUseCase = recalculateGoalProgressUseCase;
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
        List<GoalId> activeGoalIds = goalHabitLinkRepository.findActiveGoalIdsByHabitId(habitId);

        for (GoalId goalId : activeGoalIds) {
            recalculateGoalProgressUseCase.execute(goalId);
        }

        processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);

        log.info("Goal progress recalculated for {} goals: habitId={}, eventId={}",
                activeGoalIds.size(), event.habitId(), event.eventId());
    }
}
