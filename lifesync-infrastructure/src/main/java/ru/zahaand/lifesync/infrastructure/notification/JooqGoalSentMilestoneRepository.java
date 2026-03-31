package ru.zahaand.lifesync.infrastructure.notification;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.notification.GoalSentMilestoneRepository;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.GOAL_SENT_MILESTONES;

@Repository
public class JooqGoalSentMilestoneRepository implements GoalSentMilestoneRepository {

    private final DSLContext dsl;

    public JooqGoalSentMilestoneRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean existsByGoalIdAndThreshold(GoalId goalId, int threshold) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(GOAL_SENT_MILESTONES)
                        .where(GOAL_SENT_MILESTONES.GOAL_ID.eq(goalId.value()))
                        .and(GOAL_SENT_MILESTONES.THRESHOLD.eq(threshold))
        );
    }

    @Override
    public void save(GoalId goalId, int threshold) {
        dsl.insertInto(GOAL_SENT_MILESTONES)
                .set(GOAL_SENT_MILESTONES.GOAL_ID, goalId.value())
                .set(GOAL_SENT_MILESTONES.THRESHOLD, threshold)
                .execute();
    }
}
