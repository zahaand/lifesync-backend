package ru.zahaand.lifesync.application.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.lifesync.domain.goal.GoalHabitLink;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkId;
import ru.zahaand.lifesync.domain.goal.GoalHabitLinkRepository;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.exception.DuplicateGoalHabitLinkException;
import ru.zahaand.lifesync.domain.goal.exception.GoalNotFoundException;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class LinkHabitToGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(LinkHabitToGoalUseCase.class);

    private final GoalRepository goalRepository;
    private final GoalHabitLinkRepository habitLinkRepository;
    private final HabitRepository habitRepository;
    private final RecalculateGoalProgressUseCase recalculateGoalProgressUseCase;
    private final Clock clock;

    public LinkHabitToGoalUseCase(GoalRepository goalRepository,
                                  GoalHabitLinkRepository habitLinkRepository,
                                  HabitRepository habitRepository,
                                  RecalculateGoalProgressUseCase recalculateGoalProgressUseCase,
                                  Clock clock) {
        this.goalRepository = goalRepository;
        this.habitLinkRepository = habitLinkRepository;
        this.habitRepository = habitRepository;
        this.recalculateGoalProgressUseCase = recalculateGoalProgressUseCase;
        this.clock = clock;
    }

    @Transactional
    public GoalHabitLink execute(GoalId goalId, UUID userId, HabitId habitId) {
        log.debug("Linking habit {} to goal {}, userId={}", habitId.value(), goalId.value(), userId);

        goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId.value()));

        habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new HabitNotFoundException("Habit not found: " + habitId.value()));

        if (habitLinkRepository.existsByGoalIdAndHabitId(goalId, habitId)) {
            throw new DuplicateGoalHabitLinkException(
                    "Habit " + habitId.value() + " is already linked to goal " + goalId.value());
        }

        Instant now = Instant.now(clock);
        GoalHabitLinkId linkId = new GoalHabitLinkId(UUID.randomUUID());
        GoalHabitLink link = new GoalHabitLink(linkId, goalId, habitId, now, now);
        GoalHabitLink saved = habitLinkRepository.save(link);

        recalculateGoalProgressUseCase.execute(goalId);

        log.info("Habit linked: habitId={}, goalId={}", habitId.value(), goalId.value());
        return saved;
    }
}
