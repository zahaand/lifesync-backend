package ru.zahaand.lifesync.domain.goal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GoalMilestoneRepository {

    GoalMilestone save(GoalMilestone milestone);

    Optional<GoalMilestone> findByIdAndGoalId(GoalMilestoneId id, GoalId goalId);

    List<GoalMilestone> findAllActiveByGoalId(GoalId goalId);

    Map<GoalId, List<GoalMilestone>> findFirst3ActiveByGoalIds(List<GoalId> goalIds);

    GoalMilestone update(GoalMilestone milestone);
}
