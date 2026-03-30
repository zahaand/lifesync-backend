package ru.zahaand.lifesync.infrastructure.habit;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.Habit;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABITS;

@Repository
public class JooqHabitRepository implements HabitRepository {

    private final DSLContext dsl;

    public JooqHabitRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Habit save(Habit habit) {
        OffsetDateTime now = habit.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(HABITS)
                .set(HABITS.ID, habit.getId().value())
                .set(HABITS.USER_ID, habit.getUserId())
                .set(HABITS.NAME, habit.getTitle())
                .set(HABITS.DESCRIPTION, habit.getDescription())
                .set(HABITS.FREQUENCY, habit.getFrequency().name())
                .set(HABITS.TARGET_DAYS_OF_WEEK, toJsonb(habit.getTargetDaysOfWeek()))
                .set(HABITS.REMINDER_TIME, habit.getReminderTime())
                .set(HABITS.ACTIVE, habit.getActive())
                .set(HABITS.CREATED_AT, now)
                .set(HABITS.UPDATED_AT, now)
                .execute();

        return habit;
    }

    @Override
    public Optional<Habit> findByIdAndUserId(HabitId id, UUID userId) {
        return dsl.select()
                .from(HABITS)
                .where(HABITS.ID.eq(id.value()))
                .and(HABITS.USER_ID.eq(userId))
                .and(HABITS.DELETED_AT.isNull())
                .fetchOptional(this::mapToHabit);
    }

    @Override
    public HabitPage findAllByUserId(UUID userId, String status, int page, int size) {
        Condition condition = HABITS.USER_ID.eq(userId)
                .and(HABITS.DELETED_AT.isNull());

        if ("active".equals(status)) {
            condition = condition.and(HABITS.ACTIVE.isTrue());
        } else if ("inactive".equals(status)) {
            condition = condition.and(HABITS.ACTIVE.isFalse());
        }

        long totalElements = dsl.selectCount()
                .from(HABITS)
                .where(condition)
                .fetchOneInto(Long.class);

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<Habit> content = dsl.select()
                .from(HABITS)
                .where(condition)
                .orderBy(HABITS.CREATED_AT.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::mapToHabit);

        return new HabitPage(content, totalElements, totalPages, page, size);
    }

    @Override
    public Habit update(Habit habit) {
        OffsetDateTime updatedAt = habit.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(HABITS)
                .set(HABITS.NAME, habit.getTitle())
                .set(HABITS.DESCRIPTION, habit.getDescription())
                .set(HABITS.FREQUENCY, habit.getFrequency().name())
                .set(HABITS.TARGET_DAYS_OF_WEEK, toJsonb(habit.getTargetDaysOfWeek()))
                .set(HABITS.REMINDER_TIME, habit.getReminderTime())
                .set(HABITS.ACTIVE, habit.getActive())
                .set(HABITS.UPDATED_AT, updatedAt)
                .set(HABITS.DELETED_AT, habit.getDeletedAt() != null
                        ? habit.getDeletedAt().atOffset(ZoneOffset.UTC)
                        : null)
                .where(HABITS.ID.eq(habit.getId().value()))
                .and(HABITS.USER_ID.eq(habit.getUserId()))
                .execute();

        return habit;
    }

    private Habit mapToHabit(Record record) {
        OffsetDateTime deletedAt = record.get(HABITS.DELETED_AT);

        return new Habit(
                new HabitId(record.get(HABITS.ID)),
                record.get(HABITS.USER_ID),
                record.get(HABITS.NAME),
                record.get(HABITS.DESCRIPTION),
                Frequency.valueOf(record.get(HABITS.FREQUENCY)),
                fromJsonb(record.get(HABITS.TARGET_DAYS_OF_WEEK)),
                record.get(HABITS.REMINDER_TIME),
                record.get(HABITS.ACTIVE),
                record.get(HABITS.CREATED_AT).toInstant(),
                record.get(HABITS.UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null
        );
    }

    private JSONB toJsonb(DayOfWeekSet dayOfWeekSet) {
        if (dayOfWeekSet == null) {
            return null;
        }
        String json = dayOfWeekSet.days().stream()
                .map(DayOfWeek::name)
                .sorted()
                .collect(Collectors.joining("\",\"", "[\"", "\"]"));
        return JSONB.jsonb(json);
    }

    private DayOfWeekSet fromJsonb(JSONB jsonb) {
        if (jsonb == null) {
            return null;
        }
        String data = jsonb.data().replaceAll("[\\[\\]\"\\s]", "");
        if (data.isEmpty()) {
            return null;
        }
        Set<DayOfWeek> days = Arrays.stream(data.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        return new DayOfWeekSet(days);
    }
}
