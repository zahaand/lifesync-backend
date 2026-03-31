package ru.zahaand.lifesync.domain.notification;

import ru.zahaand.lifesync.domain.goal.GoalId;

public interface GoalSentMilestoneRepository {

    boolean existsByGoalIdAndThreshold(GoalId goalId, int threshold);

    void save(GoalId goalId, int threshold);
}
