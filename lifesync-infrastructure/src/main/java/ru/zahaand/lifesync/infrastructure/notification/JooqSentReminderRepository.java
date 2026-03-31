package ru.zahaand.lifesync.infrastructure.notification;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.notification.SentReminderRepository;

import java.time.LocalDate;
import java.util.UUID;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.SENT_REMINDERS;

@Repository
public class JooqSentReminderRepository implements SentReminderRepository {

    private final DSLContext dsl;

    public JooqSentReminderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean existsByHabitIdAndDate(HabitId habitId, LocalDate sentDate) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SENT_REMINDERS)
                        .where(SENT_REMINDERS.HABIT_ID.eq(habitId.value()))
                        .and(SENT_REMINDERS.SENT_DATE.eq(sentDate))
        );
    }

    @Override
    public void save(HabitId habitId, UUID userId, LocalDate sentDate) {
        dsl.insertInto(SENT_REMINDERS)
                .set(SENT_REMINDERS.HABIT_ID, habitId.value())
                .set(SENT_REMINDERS.USER_ID, userId)
                .set(SENT_REMINDERS.SENT_DATE, sentDate)
                .execute();
    }
}
