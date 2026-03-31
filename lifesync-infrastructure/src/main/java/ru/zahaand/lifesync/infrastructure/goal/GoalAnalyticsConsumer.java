package ru.zahaand.lifesync.infrastructure.goal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

@Component
public class GoalAnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(GoalAnalyticsConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-goal-analytics";

    private final ProcessedEventRepository processedEventRepository;

    public GoalAnalyticsConsumer(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
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

        log.info("Goal analytics placeholder: goalId={}", event.goalId());

        processedEventRepository.save(event.eventId(), "GoalProgressUpdatedEvent", CONSUMER_GROUP);
    }
}
