package ru.zahaand.lifesync.infrastructure.habit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;
import ru.zahaand.lifesync.infrastructure.event.ProcessedEventRepository;

@Component
public class AnalyticsUpdaterConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsUpdaterConsumer.class);
    private static final String CONSUMER_GROUP = "lifesync-analytics-updater";

    private final ProcessedEventRepository processedEventRepository;

    public AnalyticsUpdaterConsumer(ProcessedEventRepository processedEventRepository) {
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

        log.info("Analytics cache invalidated: userId={}, habitId={}", event.userId(), event.habitId());

        processedEventRepository.save(event.eventId(), "HabitCompletedEvent", CONSUMER_GROUP);
    }
}
