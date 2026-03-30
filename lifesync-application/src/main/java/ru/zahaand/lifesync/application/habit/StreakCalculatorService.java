package ru.zahaand.lifesync.application.habit;

import ru.zahaand.lifesync.domain.habit.DayOfWeekSet;
import ru.zahaand.lifesync.domain.habit.Frequency;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitStreak;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StreakCalculatorService {

    private final Clock clock;

    public StreakCalculatorService(Clock clock) {
        this.clock = clock;
    }

    public HabitStreak calculate(HabitId habitId, Frequency frequency, DayOfWeekSet targetDaysOfWeek,
                                  List<LocalDate> logDatesDesc) {
        if (logDatesDesc.isEmpty()) {
            return new HabitStreak(habitId, 0, 0, null);
        }

        LocalDate lastLogDate = logDatesDesc.getFirst();

        return switch (frequency) {
            case DAILY -> calculateDaily(habitId, logDatesDesc, lastLogDate);
            case WEEKLY -> calculateWeekly(habitId, logDatesDesc, lastLogDate);
            case CUSTOM -> calculateCustom(habitId, targetDaysOfWeek, logDatesDesc, lastLogDate);
        };
    }

    private HabitStreak calculateDaily(HabitId habitId, List<LocalDate> logDatesDesc, LocalDate lastLogDate) {
        LocalDate today = LocalDate.now(clock);
        Set<LocalDate> logDateSet = new HashSet<>(logDatesDesc);

        int currentStreak = 0;
        int longestStreak = 0;
        int streak = 0;

        LocalDate earliest = logDatesDesc.getLast();
        LocalDate date = logDatesDesc.getFirst();

        // Calculate longest streak by scanning all dates from earliest to latest
        LocalDate d = earliest;
        while (!d.isAfter(date)) {
            if (logDateSet.contains(d)) {
                streak++;
                if (streak > longestStreak) {
                    longestStreak = streak;
                }
            } else {
                streak = 0;
            }
            d = d.plusDays(1);
        }

        // Calculate current streak: walk backwards from today or yesterday
        LocalDate startDate = logDateSet.contains(today) ? today : today.minusDays(1);
        if (logDateSet.contains(startDate)) {
            LocalDate cursor = startDate;
            while (logDateSet.contains(cursor)) {
                currentStreak++;
                cursor = cursor.minusDays(1);
            }
        }

        if (longestStreak < currentStreak) {
            longestStreak = currentStreak;
        }

        return new HabitStreak(habitId, currentStreak, longestStreak, lastLogDate);
    }

    private HabitStreak calculateWeekly(HabitId habitId, List<LocalDate> logDatesDesc, LocalDate lastLogDate) {
        LocalDate today = LocalDate.now(clock);
        Set<Integer> weekYears = new HashSet<>();

        // Collect all unique ISO week-year pairs
        for (LocalDate date : logDatesDesc) {
            int weekYear = date.get(IsoFields.WEEK_BASED_YEAR) * 100 + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            weekYears.add(weekYear);
        }

        // Current streak: count consecutive weeks ending at current or previous week
        int currentWeekYear = today.get(IsoFields.WEEK_BASED_YEAR) * 100 + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        LocalDate cursor = today;
        if (!weekYears.contains(currentWeekYear)) {
            cursor = cursor.minusWeeks(1);
            currentWeekYear = cursor.get(IsoFields.WEEK_BASED_YEAR) * 100 + cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        }

        int currentStreak = 0;
        while (weekYears.contains(currentWeekYear)) {
            currentStreak++;
            cursor = cursor.minusWeeks(1);
            currentWeekYear = cursor.get(IsoFields.WEEK_BASED_YEAR) * 100 + cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        }

        // Longest streak: sort all weeks and find longest consecutive run
        List<Integer> sortedWeeks = weekYears.stream().sorted().toList();
        int longestStreak = 0;
        int streak = 1;
        for (int i = 1; i < sortedWeeks.size(); i++) {
            if (areConsecutiveWeeks(sortedWeeks.get(i - 1), sortedWeeks.get(i))) {
                streak++;
            } else {
                streak = 1;
            }
            if (streak > longestStreak) {
                longestStreak = streak;
            }
        }
        if (sortedWeeks.size() == 1 || longestStreak == 0) {
            longestStreak = Math.max(longestStreak, 1);
        }

        if (longestStreak < currentStreak) {
            longestStreak = currentStreak;
        }

        return new HabitStreak(habitId, currentStreak, longestStreak, lastLogDate);
    }

    private boolean areConsecutiveWeeks(int weekYear1, int weekYear2) {
        int year1 = weekYear1 / 100;
        int week1 = weekYear1 % 100;
        int year2 = weekYear2 / 100;
        int week2 = weekYear2 % 100;

        if (year1 == year2) {
            return week2 - week1 == 1;
        }
        if (year2 - year1 == 1) {
            // Check if week1 is last week of year1 and week2 is 1
            LocalDate lastDayOfYear1 = LocalDate.of(year1, 12, 28);
            int maxWeek = lastDayOfYear1.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return week1 == maxWeek && week2 == 1;
        }
        return false;
    }

    private HabitStreak calculateCustom(HabitId habitId, DayOfWeekSet targetDaysOfWeek,
                                         List<LocalDate> logDatesDesc, LocalDate lastLogDate) {
        LocalDate today = LocalDate.now(clock);
        Set<LocalDate> logDateSet = new HashSet<>(logDatesDesc);

        // Current streak: walk backwards from today through target days
        int currentStreak = 0;
        LocalDate cursor = today;
        // If today is not a target day, find the previous target day
        while (!targetDaysOfWeek.contains(cursor.getDayOfWeek()) && cursor.isAfter(today.minusWeeks(1))) {
            cursor = cursor.minusDays(1);
        }
        // Allow today or the previous target day
        if (targetDaysOfWeek.contains(cursor.getDayOfWeek())) {
            // If latest target day was logged, or it's today (might not be logged yet — only count if logged)
            while (targetDaysOfWeek.contains(cursor.getDayOfWeek()) && logDateSet.contains(cursor)) {
                currentStreak++;
                cursor = cursor.minusDays(1);
                // Skip non-target days
                while (!targetDaysOfWeek.contains(cursor.getDayOfWeek())) {
                    cursor = cursor.minusDays(1);
                }
            }
            // If the cursor lands on a target day that's today and not logged, the streak hasn't broken yet
            // (it just means today hasn't been completed yet). But if the missed target day is in the past, streak = 0.
            // Actually, if we didn't enter the while, currentStreak stays 0.
        }

        // Longest streak: scan all target days from earliest log to latest
        int longestStreak = 0;
        if (!logDatesDesc.isEmpty()) {
            LocalDate earliest = logDatesDesc.getLast();
            LocalDate latest = logDatesDesc.getFirst();
            int streak = 0;
            LocalDate d = earliest;
            while (!d.isAfter(latest)) {
                if (targetDaysOfWeek.contains(d.getDayOfWeek())) {
                    if (logDateSet.contains(d)) {
                        streak++;
                        if (streak > longestStreak) {
                            longestStreak = streak;
                        }
                    } else {
                        streak = 0;
                    }
                }
                d = d.plusDays(1);
            }
        }

        if (longestStreak < currentStreak) {
            longestStreak = currentStreak;
        }

        return new HabitStreak(habitId, currentStreak, longestStreak, lastLogDate);
    }
}
