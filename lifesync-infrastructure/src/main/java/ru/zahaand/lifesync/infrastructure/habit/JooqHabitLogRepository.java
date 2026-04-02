package ru.zahaand.lifesync.infrastructure.habit;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitLog;
import ru.zahaand.lifesync.domain.habit.HabitLogId;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABIT_LOGS;

@Repository
public class JooqHabitLogRepository implements HabitLogRepository {

    private final DSLContext dsl;

    public JooqHabitLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public HabitLog save(HabitLog habitLog) {
        OffsetDateTime now = habitLog.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(HABIT_LOGS)
                .set(HABIT_LOGS.ID, habitLog.getId().value())
                .set(HABIT_LOGS.HABIT_ID, habitLog.getHabitId().value())
                .set(HABIT_LOGS.USER_ID, habitLog.getUserId())
                .set(HABIT_LOGS.LOG_DATE, habitLog.getLogDate())
                .set(HABIT_LOGS.NOTE, habitLog.getNote())
                .set(HABIT_LOGS.CREATED_AT, now)
                .set(HABIT_LOGS.UPDATED_AT, now)
                .execute();

        return habitLog;
    }

    @Override
    public Optional<HabitLog> findByIdAndUserId(HabitLogId id, UUID userId) {
        return dsl.select()
                .from(HABIT_LOGS)
                .where(HABIT_LOGS.ID.eq(id.value()))
                .and(HABIT_LOGS.USER_ID.eq(userId))
                .and(HABIT_LOGS.DELETED_AT.isNull())
                .fetchOptional(this::mapToHabitLog);
    }

    @Override
    public HabitLogPage findByHabitIdAndUserId(HabitId habitId, UUID userId, int page, int size) {
        var condition = HABIT_LOGS.HABIT_ID.eq(habitId.value())
                .and(HABIT_LOGS.USER_ID.eq(userId))
                .and(HABIT_LOGS.DELETED_AT.isNull());

        long totalElements = dsl.selectCount()
                .from(HABIT_LOGS)
                .where(condition)
                .fetchOneInto(Long.class);

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<HabitLog> content = dsl.select()
                .from(HABIT_LOGS)
                .where(condition)
                .orderBy(HABIT_LOGS.LOG_DATE.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::mapToHabitLog);

        return new HabitLogPage(content, totalElements, totalPages, page, size);
    }

    @Override
    public Optional<HabitLog> findByHabitIdAndLogDateAndUserId(HabitId habitId, LocalDate logDate, UUID userId) {
        return dsl.select()
                .from(HABIT_LOGS)
                .where(HABIT_LOGS.HABIT_ID.eq(habitId.value()))
                .and(HABIT_LOGS.LOG_DATE.eq(logDate))
                .and(HABIT_LOGS.USER_ID.eq(userId))
                .and(HABIT_LOGS.DELETED_AT.isNull())
                .fetchOptional(this::mapToHabitLog);
    }

    @Override
    public HabitLog update(HabitLog habitLog) {
        OffsetDateTime updatedAt = habitLog.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(HABIT_LOGS)
                .set(HABIT_LOGS.NOTE, habitLog.getNote())
                .set(HABIT_LOGS.UPDATED_AT, updatedAt)
                .set(HABIT_LOGS.DELETED_AT, habitLog.getDeletedAt() != null
                        ? habitLog.getDeletedAt().atOffset(ZoneOffset.UTC)
                        : null)
                .where(HABIT_LOGS.ID.eq(habitLog.getId().value()))
                .and(HABIT_LOGS.USER_ID.eq(habitLog.getUserId()))
                .execute();

        return habitLog;
    }

    @Override
    public Map<HabitId, HabitLog> findTodayLogsByHabitIds(List<HabitId> habitIds, LocalDate today) {
        if (habitIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = habitIds.stream().map(HabitId::value).toList();

        return dsl.select()
                .from(HABIT_LOGS)
                .where(HABIT_LOGS.HABIT_ID.in(ids))
                .and(HABIT_LOGS.LOG_DATE.eq(today))
                .and(HABIT_LOGS.DELETED_AT.isNull())
                .fetch(this::mapToHabitLog)
                .stream()
                .collect(Collectors.toMap(HabitLog::getHabitId, Function.identity()));
    }

    @Override
    public List<LocalDate> findLogDatesDesc(HabitId habitId, UUID userId) {
        return dsl.select(HABIT_LOGS.LOG_DATE)
                .from(HABIT_LOGS)
                .where(HABIT_LOGS.HABIT_ID.eq(habitId.value()))
                .and(HABIT_LOGS.USER_ID.eq(userId))
                .and(HABIT_LOGS.DELETED_AT.isNull())
                .orderBy(HABIT_LOGS.LOG_DATE.desc())
                .fetchInto(LocalDate.class);
    }

    private HabitLog mapToHabitLog(Record record) {
        OffsetDateTime deletedAt = record.get(HABIT_LOGS.DELETED_AT);

        return new HabitLog(
                new HabitLogId(record.get(HABIT_LOGS.ID)),
                new HabitId(record.get(HABIT_LOGS.HABIT_ID)),
                record.get(HABIT_LOGS.USER_ID),
                record.get(HABIT_LOGS.LOG_DATE),
                record.get(HABIT_LOGS.NOTE),
                record.get(HABIT_LOGS.CREATED_AT).toInstant(),
                record.get(HABIT_LOGS.UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null
        );
    }
}
