package ru.zahaand.lifesync.domain.goal;

import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.LocalDate;
import java.util.List;

public interface GoalHabitLinkRepository {

    GoalHabitLink save(GoalHabitLink link);

    boolean existsByGoalIdAndHabitId(GoalId goalId, HabitId habitId);

    List<GoalHabitLink> findAllByGoalId(GoalId goalId);

    List<GoalId> findActiveGoalIdsByHabitId(HabitId habitId);

    void deleteByGoalIdAndHabitId(GoalId goalId, HabitId habitId);

    int countTotalByGoalId(GoalId goalId);

    int countCompletedDaysByGoalId(GoalId goalId);

    int countExpectedCompletionsByGoalId(GoalId goalId, LocalDate createdAt, LocalDate endDate);
}
