package ru.zahaand.lifesync.domain.goal;

import java.util.List;
import java.util.Optional;

public interface GoalMilestoneRepository {

    GoalMilestone save(GoalMilestone milestone);

    Optional<GoalMilestone> findByIdAndGoalId(GoalMilestoneId id, GoalId goalId);

    List<GoalMilestone> findAllActiveByGoalId(GoalId goalId);

    GoalMilestone update(GoalMilestone milestone);
}
