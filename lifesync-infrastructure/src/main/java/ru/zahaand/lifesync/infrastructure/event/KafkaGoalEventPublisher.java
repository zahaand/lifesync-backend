package ru.zahaand.lifesync.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.zahaand.lifesync.domain.event.GoalProgressUpdatedEvent;

@Component
public class KafkaGoalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaGoalEventPublisher.class);
    private static final String TOPIC = "goal.progress.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaGoalEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGoalProgressUpdatedEvent(GoalProgressUpdatedEvent event) {
        String partitionKey = event.goalId().toString();

        kafkaTemplate.send(TOPIC, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish GoalProgressUpdatedEvent to Kafka: goalId={}, userId={}, error={}",
                                event.goalId(), event.userId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Published GoalProgressUpdatedEvent to Kafka: goalId={}, userId={}, topic={}, partition={}, offset={}",
                                event.goalId(), event.userId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
