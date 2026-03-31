package ru.zahaand.lifesync.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.zahaand.lifesync.domain.event.HabitCompletedEvent;

@Component
public class KafkaHabitEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaHabitEventPublisher.class);
    private static final String TOPIC = "habit.log.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaHabitEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleHabitCompletedEvent(HabitCompletedEvent event) {
        String partitionKey = event.habitId().toString();

        kafkaTemplate.send(TOPIC, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish HabitCompletedEvent to Kafka: habitId={}, userId={}, error={}",
                                event.habitId(), event.userId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Published HabitCompletedEvent to Kafka: habitId={}, userId={}, topic={}, partition={}, offset={}",
                                event.habitId(), event.userId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
