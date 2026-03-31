package ru.zahaand.lifesync.infrastructure.goal;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.GOALS;

@Repository
public class JooqGoalRepository implements GoalRepository {

    private final DSLContext dsl;

    public JooqGoalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Goal save(Goal goal) {
        OffsetDateTime now = goal.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(GOALS)
                .set(GOALS.ID, goal.getId().value())
                .set(GOALS.USER_ID, goal.getUserId())
                .set(GOALS.TITLE, goal.getTitle())
                .set(GOALS.DESCRIPTION, goal.getDescription())
                .set(GOALS.TARGET_DATE, goal.getTargetDate())
                .set(GOALS.STATUS, goal.getStatus().name())
                .set(GOALS.PROGRESS, goal.getProgress())
                .set(GOALS.CREATED_AT, now)
                .set(GOALS.UPDATED_AT, now)
                .execute();

        return goal;
    }

    @Override
    public Optional<Goal> findById(GoalId id) {
        return dsl.select()
                .from(GOALS)
                .where(GOALS.ID.eq(id.value()))
                .and(GOALS.DELETED_AT.isNull())
                .fetchOptional(this::mapToGoal);
    }

    @Override
    public Optional<Goal> findByIdAndUserId(GoalId id, UUID userId) {
        return dsl.select()
                .from(GOALS)
                .where(GOALS.ID.eq(id.value()))
                .and(GOALS.USER_ID.eq(userId))
                .and(GOALS.DELETED_AT.isNull())
                .fetchOptional(this::mapToGoal);
    }

    @Override
    public GoalPage findAllByUserId(UUID userId, GoalStatus status, int page, int size) {
        Condition condition = GOALS.USER_ID.eq(userId)
                .and(GOALS.DELETED_AT.isNull());

        if (status != null) {
            condition = condition.and(GOALS.STATUS.eq(status.name()));
        }

        long totalElements = dsl.selectCount()
                .from(GOALS)
                .where(condition)
                .fetchOneInto(Long.class);

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<Goal> content = dsl.select()
                .from(GOALS)
                .where(condition)
                .orderBy(GOALS.CREATED_AT.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::mapToGoal);

        return new GoalPage(content, totalElements, totalPages, page, size);
    }

    @Override
    public Goal update(Goal goal) {
        OffsetDateTime updatedAt = goal.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(GOALS)
                .set(GOALS.TITLE, goal.getTitle())
                .set(GOALS.DESCRIPTION, goal.getDescription())
                .set(GOALS.TARGET_DATE, goal.getTargetDate())
                .set(GOALS.STATUS, goal.getStatus().name())
                .set(GOALS.PROGRESS, goal.getProgress())
                .set(GOALS.UPDATED_AT, updatedAt)
                .set(GOALS.DELETED_AT, goal.getDeletedAt() != null
                        ? goal.getDeletedAt().atOffset(ZoneOffset.UTC)
                        : null)
                .where(GOALS.ID.eq(goal.getId().value()))
                .execute();

        return goal;
    }

    private Goal mapToGoal(Record record) {
        OffsetDateTime deletedAt = record.get(GOALS.DELETED_AT);

        return new Goal(
                new GoalId(record.get(GOALS.ID)),
                record.get(GOALS.USER_ID),
                record.get(GOALS.TITLE),
                record.get(GOALS.DESCRIPTION),
                record.get(GOALS.TARGET_DATE),
                GoalStatus.valueOf(record.get(GOALS.STATUS)),
                record.get(GOALS.PROGRESS),
                record.get(GOALS.CREATED_AT).toInstant(),
                record.get(GOALS.UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null
        );
    }
}
