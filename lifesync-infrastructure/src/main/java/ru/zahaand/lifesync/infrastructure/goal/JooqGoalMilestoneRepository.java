package ru.zahaand.lifesync.infrastructure.goal;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneId;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.GOAL_MILESTONES;

@Repository
public class JooqGoalMilestoneRepository implements GoalMilestoneRepository {

    private final DSLContext dsl;

    public JooqGoalMilestoneRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public GoalMilestone save(GoalMilestone milestone) {
        OffsetDateTime now = milestone.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(GOAL_MILESTONES)
                .set(GOAL_MILESTONES.ID, milestone.getId().value())
                .set(GOAL_MILESTONES.GOAL_ID, milestone.getGoalId().value())
                .set(GOAL_MILESTONES.TITLE, milestone.getTitle())
                .set(GOAL_MILESTONES.SORT_ORDER, milestone.getSortOrder())
                .set(GOAL_MILESTONES.COMPLETED, milestone.getCompleted())
                .set(GOAL_MILESTONES.COMPLETED_AT, milestone.getCompletedAt() != null
                        ? milestone.getCompletedAt().atOffset(ZoneOffset.UTC) : null)
                .set(GOAL_MILESTONES.CREATED_AT, now)
                .set(GOAL_MILESTONES.UPDATED_AT, now)
                .execute();

        return milestone;
    }

    @Override
    public Optional<GoalMilestone> findByIdAndGoalId(GoalMilestoneId id, GoalId goalId) {
        return dsl.select()
                .from(GOAL_MILESTONES)
                .where(GOAL_MILESTONES.ID.eq(id.value()))
                .and(GOAL_MILESTONES.GOAL_ID.eq(goalId.value()))
                .and(GOAL_MILESTONES.DELETED_AT.isNull())
                .fetchOptional(this::mapToMilestone);
    }

    @Override
    public List<GoalMilestone> findAllActiveByGoalId(GoalId goalId) {
        return dsl.select()
                .from(GOAL_MILESTONES)
                .where(GOAL_MILESTONES.GOAL_ID.eq(goalId.value()))
                .and(GOAL_MILESTONES.DELETED_AT.isNull())
                .orderBy(GOAL_MILESTONES.SORT_ORDER.asc())
                .fetch(this::mapToMilestone);
    }

    @Override
    public Map<GoalId, List<GoalMilestone>> findFirst3ActiveByGoalIds(List<GoalId> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = goalIds.stream().map(GoalId::value).toList();

        List<GoalMilestone> allMilestones = dsl.select()
                .from(GOAL_MILESTONES)
                .where(GOAL_MILESTONES.GOAL_ID.in(ids))
                .and(GOAL_MILESTONES.DELETED_AT.isNull())
                .orderBy(GOAL_MILESTONES.SORT_ORDER.asc())
                .fetch(this::mapToMilestone);

        Map<GoalId, List<GoalMilestone>> result = new LinkedHashMap<>();
        for (GoalMilestone milestone : allMilestones) {
            result.computeIfAbsent(milestone.getGoalId(), k -> new ArrayList<>());
            List<GoalMilestone> list = result.get(milestone.getGoalId());
            if (list.size() < 3) {
                list.add(milestone);
            }
        }

        return result;
    }

    @Override
    public GoalMilestone update(GoalMilestone milestone) {
        OffsetDateTime updatedAt = milestone.getUpdatedAt().atOffset(ZoneOffset.UTC);

        dsl.update(GOAL_MILESTONES)
                .set(GOAL_MILESTONES.TITLE, milestone.getTitle())
                .set(GOAL_MILESTONES.SORT_ORDER, milestone.getSortOrder())
                .set(GOAL_MILESTONES.COMPLETED, milestone.getCompleted())
                .set(GOAL_MILESTONES.COMPLETED_AT, milestone.getCompletedAt() != null
                        ? milestone.getCompletedAt().atOffset(ZoneOffset.UTC) : null)
                .set(GOAL_MILESTONES.UPDATED_AT, updatedAt)
                .set(GOAL_MILESTONES.DELETED_AT, milestone.getDeletedAt() != null
                        ? milestone.getDeletedAt().atOffset(ZoneOffset.UTC) : null)
                .where(GOAL_MILESTONES.ID.eq(milestone.getId().value()))
                .execute();

        return milestone;
    }

    private GoalMilestone mapToMilestone(Record record) {
        OffsetDateTime completedAt = record.get(GOAL_MILESTONES.COMPLETED_AT);
        OffsetDateTime deletedAt = record.get(GOAL_MILESTONES.DELETED_AT);

        return new GoalMilestone(
                new GoalMilestoneId(record.get(GOAL_MILESTONES.ID)),
                new GoalId(record.get(GOAL_MILESTONES.GOAL_ID)),
                record.get(GOAL_MILESTONES.TITLE),
                record.get(GOAL_MILESTONES.SORT_ORDER),
                record.get(GOAL_MILESTONES.COMPLETED),
                completedAt != null ? completedAt.toInstant() : null,
                record.get(GOAL_MILESTONES.CREATED_AT).toInstant(),
                record.get(GOAL_MILESTONES.UPDATED_AT).toInstant(),
                deletedAt != null ? deletedAt.toInstant() : null
        );
    }
}
