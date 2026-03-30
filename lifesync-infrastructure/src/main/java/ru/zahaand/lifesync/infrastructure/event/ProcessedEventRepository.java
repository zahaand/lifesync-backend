package ru.zahaand.lifesync.infrastructure.event;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.PROCESSED_EVENTS;

@Repository
public class ProcessedEventRepository {

    private final DSLContext dsl;

    public ProcessedEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(PROCESSED_EVENTS)
                        .where(PROCESSED_EVENTS.EVENT_ID.eq(eventId))
                        .and(PROCESSED_EVENTS.CONSUMER_GROUP.eq(consumerGroup))
        );
    }

    public void save(String eventId, String eventType, String consumerGroup) {
        dsl.insertInto(PROCESSED_EVENTS)
                .set(PROCESSED_EVENTS.EVENT_ID, eventId)
                .set(PROCESSED_EVENTS.EVENT_TYPE, eventType)
                .set(PROCESSED_EVENTS.CONSUMER_GROUP, consumerGroup)
                .execute();
    }
}
