package ru.zahaand.lifesync.infrastructure.goal;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.goal.GoalHabitLink;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkId;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.habit.HabitId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.countDistinct;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.GOALS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.GOAL_HABITS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABITS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABIT_LOGS;

@Repository
public class JooqGoalHabitLinkRepository implements GoalHabitLinkRepository {

    private final DSLContext dsl;

    public JooqGoalHabitLinkRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public GoalHabitLink save(GoalHabitLink link) {
        OffsetDateTime now = link.getCreatedAt().atOffset(ZoneOffset.UTC);

        dsl.insertInto(GOAL_HABITS)
                .set(GOAL_HABITS.ID, link.getId().value())
                .set(GOAL_HABITS.GOAL_ID, link.getGoalId().value())
                .set(GOAL_HABITS.HABIT_ID, link.getHabitId().value())
                .set(GOAL_HABITS.CREATED_AT, now)
                .set(GOAL_HABITS.UPDATED_AT, now)
                .execute();

        return link;
    }

    @Override
    public boolean existsByGoalIdAndHabitId(GoalId goalId, HabitId habitId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(GOAL_HABITS)
                        .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                        .and(GOAL_HABITS.HABIT_ID.eq(habitId.value()))
        );
    }

    @Override
    public List<GoalHabitLink> findAllByGoalId(GoalId goalId) {
        return dsl.select()
                .from(GOAL_HABITS)
                .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                .fetch(this::mapToLink);
    }

    @Override
    public List<GoalId> findActiveGoalIdsByHabitId(HabitId habitId) {
        return dsl.select(GOAL_HABITS.GOAL_ID)
                .from(GOAL_HABITS)
                .join(GOALS).on(GOAL_HABITS.GOAL_ID.eq(GOALS.ID))
                .where(GOAL_HABITS.HABIT_ID.eq(habitId.value()))
                .and(GOALS.DELETED_AT.isNull())
                .fetch(record -> new GoalId(record.get(GOAL_HABITS.GOAL_ID)));
    }

    @Override
    public void deleteByGoalIdAndHabitId(GoalId goalId, HabitId habitId) {
        dsl.deleteFrom(GOAL_HABITS)
                .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                .and(GOAL_HABITS.HABIT_ID.eq(habitId.value()))
                .execute();
    }

    @Override
    public int countTotalByGoalId(GoalId goalId) {
        return dsl.selectCount()
                .from(GOAL_HABITS)
                .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                .fetchOneInto(Integer.class);
    }

    @Override
    public int countCompletedDaysByGoalId(GoalId goalId) {
        Record1<Integer> result = dsl.select(countDistinct(HABIT_LOGS.LOG_DATE))
                .from(GOAL_HABITS)
                .join(HABIT_LOGS).on(GOAL_HABITS.HABIT_ID.eq(HABIT_LOGS.HABIT_ID))
                .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                .and(HABIT_LOGS.DELETED_AT.isNull())
                .fetchOne();

        return result != null ? result.value1() : 0;
    }

    @Override
    public int countExpectedCompletionsByGoalId(GoalId goalId, LocalDate createdAt, LocalDate endDate) {
        List<HabitFrequencyInfo> habits = dsl.select(HABITS.FREQUENCY, HABITS.TARGET_DAYS_OF_WEEK)
                .from(GOAL_HABITS)
                .join(HABITS).on(GOAL_HABITS.HABIT_ID.eq(HABITS.ID))
                .where(GOAL_HABITS.GOAL_ID.eq(goalId.value()))
                .fetch(record -> new HabitFrequencyInfo(
                        record.get(HABITS.FREQUENCY),
                        record.get(HABITS.TARGET_DAYS_OF_WEEK)
                ));

        int total = 0;
        for (HabitFrequencyInfo habit : habits) {
            total += calculateExpected(habit.frequency(), habit.targetDaysOfWeek(), createdAt, endDate);
        }
        return total;
    }

    private int calculateExpected(String frequency, JSONB targetDaysOfWeek,
                                  LocalDate createdAt, LocalDate endDate) {
        if (createdAt.isAfter(endDate)) {
            return 0;
        }

        long totalDays = ChronoUnit.DAYS.between(createdAt, endDate) + 1;

        return switch (frequency) {
            case "DAILY" -> (int) totalDays;
            case "WEEKLY" -> countWeeks(createdAt, endDate);
            case "CUSTOM" -> countCustomDays(targetDaysOfWeek, createdAt, endDate);
            default -> 0;
        };
    }

    private int countWeeks(LocalDate start, LocalDate end) {
        LocalDate firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) (ChronoUnit.WEEKS.between(firstMonday, lastMonday) + 1);
    }

    private int countCustomDays(JSONB targetDaysOfWeek, LocalDate start, LocalDate end) {
        if (targetDaysOfWeek == null) {
            return 0;
        }

        Set<DayOfWeek> targetDays = parseDaysOfWeek(targetDaysOfWeek);
        if (targetDays.isEmpty()) {
            return 0;
        }

        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (targetDays.contains(current.getDayOfWeek())) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    private Set<DayOfWeek> parseDaysOfWeek(JSONB jsonb) {
        String data = jsonb.data().replaceAll("[\\[\\]\"\\s]", "");
        if (data.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(data.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private GoalHabitLink mapToLink(Record record) {
        return new GoalHabitLink(
                new GoalHabitLinkId(record.get(GOAL_HABITS.ID)),
                new GoalId(record.get(GOAL_HABITS.GOAL_ID)),
                new HabitId(record.get(GOAL_HABITS.HABIT_ID)),
                record.get(GOAL_HABITS.CREATED_AT).toInstant(),
                record.get(GOAL_HABITS.UPDATED_AT).toInstant()
        );
    }

    private record HabitFrequencyInfo(String frequency, JSONB targetDaysOfWeek) {
    }
}
