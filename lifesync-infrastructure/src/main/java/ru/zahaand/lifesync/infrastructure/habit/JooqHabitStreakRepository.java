package ru.zahaand.lifesync.infrastructure.habit;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitStreak;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABITS;
import static ru.zahaand.lifesync.infrastructure.generated.Tables.HABIT_STREAKS;

@Repository
public class JooqHabitStreakRepository implements HabitStreakRepository {

    private final DSLContext dsl;

    public JooqHabitStreakRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<HabitStreak> findByHabitIdAndUserId(HabitId habitId, UUID userId) {
        return dsl.select(HABIT_STREAKS.HABIT_ID, HABIT_STREAKS.CURRENT_STREAK,
                        HABIT_STREAKS.LONGEST_STREAK, HABIT_STREAKS.LAST_LOG_DATE)
                .from(HABIT_STREAKS)
                .join(HABITS).on(HABIT_STREAKS.HABIT_ID.eq(HABITS.ID))
                .where(HABIT_STREAKS.HABIT_ID.eq(habitId.value()))
                .and(HABITS.USER_ID.eq(userId))
                .and(HABITS.DELETED_AT.isNull())
                .fetchOptional(this::mapToHabitStreak);
    }

    @Override
    public Map<HabitId, HabitStreak> findByHabitIdsAndUserId(List<HabitId> habitIds, UUID userId) {
        if (habitIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = habitIds.stream().map(HabitId::value).toList();

        return dsl.select(HABIT_STREAKS.HABIT_ID, HABIT_STREAKS.CURRENT_STREAK,
                        HABIT_STREAKS.LONGEST_STREAK, HABIT_STREAKS.LAST_LOG_DATE)
                .from(HABIT_STREAKS)
                .join(HABITS).on(HABIT_STREAKS.HABIT_ID.eq(HABITS.ID))
                .where(HABIT_STREAKS.HABIT_ID.in(ids))
                .and(HABITS.USER_ID.eq(userId))
                .and(HABITS.DELETED_AT.isNull())
                .fetch(this::mapToHabitStreak)
                .stream()
                .collect(Collectors.toMap(HabitStreak::habitId, Function.identity()));
    }

    @Override
    public HabitStreak save(HabitStreak streak) {
        dsl.insertInto(HABIT_STREAKS)
                .set(HABIT_STREAKS.ID, UUID.randomUUID())
                .set(HABIT_STREAKS.HABIT_ID, streak.habitId().value())
                .set(HABIT_STREAKS.CURRENT_STREAK, streak.currentStreak())
                .set(HABIT_STREAKS.LONGEST_STREAK, streak.longestStreak())
                .set(HABIT_STREAKS.LAST_LOG_DATE, streak.lastLogDate())
                .execute();

        return streak;
    }

    @Override
    public HabitStreak update(HabitStreak streak) {
        dsl.update(HABIT_STREAKS)
                .set(HABIT_STREAKS.CURRENT_STREAK, streak.currentStreak())
                .set(HABIT_STREAKS.LONGEST_STREAK, streak.longestStreak())
                .set(HABIT_STREAKS.LAST_LOG_DATE, streak.lastLogDate())
                .where(HABIT_STREAKS.HABIT_ID.eq(streak.habitId().value()))
                .execute();

        return streak;
    }

    private HabitStreak mapToHabitStreak(Record record) {
        return new HabitStreak(
                new HabitId(record.get(HABIT_STREAKS.HABIT_ID)),
                record.get(HABIT_STREAKS.CURRENT_STREAK),
                record.get(HABIT_STREAKS.LONGEST_STREAK),
                record.get(HABIT_STREAKS.LAST_LOG_DATE)
        );
    }
}
