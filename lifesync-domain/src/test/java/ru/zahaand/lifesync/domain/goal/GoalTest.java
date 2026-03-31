package ru.zahaand.lifesync.domain.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoalTest {

    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private Goal createGoal(int progress, GoalStatus status) {
        return new Goal(GOAL_ID, USER_ID, "Test goal", "desc",
                LocalDate.of(2026, 12, 31), status, progress, NOW, NOW, null);
    }

    @Nested
    class Constructor {

        @Test
        @DisplayName("Should reject progress below 0")
        void shouldRejectNegativeProgress() {
            assertThrows(IllegalArgumentException.class,
                    () -> createGoal(-1, GoalStatus.ACTIVE));
        }

        @Test
        @DisplayName("Should reject progress above 100")
        void shouldRejectProgressAbove100() {
            assertThrows(IllegalArgumentException.class,
                    () -> createGoal(101, GoalStatus.ACTIVE));
        }

        @Test
        @DisplayName("Should accept progress at boundaries 0 and 100")
        void shouldAcceptBoundaryProgress() {
            assertDoesNotThrow(() -> createGoal(0, GoalStatus.ACTIVE));
            assertDoesNotThrow(() -> createGoal(100, GoalStatus.ACTIVE));
        }

        @Test
        @DisplayName("Should reject null title")
        void shouldRejectNullTitle() {
            assertThrows(NullPointerException.class,
                    () -> new Goal(GOAL_ID, USER_ID, null, null, null,
                            GoalStatus.ACTIVE, 0, NOW, NOW, null));
        }
    }

    @Nested
    class UpdateProgress {

        @Test
        @DisplayName("Should set status to COMPLETED when progress reaches 100")
        void shouldSetCompletedAt100() {
            Goal goal = createGoal(50, GoalStatus.ACTIVE);
            Instant later = NOW.plusSeconds(60);
            Goal updated = goal.updateProgress(100, later);

            assertEquals(100, updated.getProgress());
            assertEquals(GoalStatus.COMPLETED, updated.getStatus());
        }

        @Test
        @DisplayName("Should keep ACTIVE status when progress is below 100")
        void shouldKeepActiveBelow100() {
            Goal goal = createGoal(0, GoalStatus.ACTIVE);
            Goal updated = goal.updateProgress(50, NOW.plusSeconds(60));

            assertEquals(50, updated.getProgress());
            assertEquals(GoalStatus.ACTIVE, updated.getStatus());
        }

        @Test
        @DisplayName("Should update updatedAt timestamp")
        void shouldUpdateTimestamp() {
            Goal goal = createGoal(0, GoalStatus.ACTIVE);
            Instant later = NOW.plusSeconds(60);
            Goal updated = goal.updateProgress(30, later);

            assertEquals(later, updated.getUpdatedAt());
        }
    }

    @Nested
    class SoftDelete {

        @Test
        @DisplayName("Should set deletedAt on soft delete")
        void shouldSetDeletedAt() {
            Goal goal = createGoal(0, GoalStatus.ACTIVE);
            Instant deleteTime = NOW.plusSeconds(100);
            Goal deleted = goal.softDelete(deleteTime);

            assertTrue(deleted.isDeleted());
            assertEquals(deleteTime, deleted.getDeletedAt());
        }
    }

    @Nested
    class IsActive {

        @Test
        @DisplayName("Should return true for ACTIVE non-deleted goal")
        void shouldReturnTrueForActive() {
            Goal goal = createGoal(0, GoalStatus.ACTIVE);
            assertTrue(goal.isActive());
        }

        @Test
        @DisplayName("Should return false for COMPLETED goal")
        void shouldReturnFalseForCompleted() {
            Goal goal = createGoal(100, GoalStatus.COMPLETED);
            assertFalse(goal.isActive());
        }

        @Test
        @DisplayName("Should return false for deleted goal")
        void shouldReturnFalseForDeleted() {
            Goal goal = createGoal(0, GoalStatus.ACTIVE);
            Goal deleted = goal.softDelete(NOW.plusSeconds(1));
            assertFalse(deleted.isActive());
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("Should update title, description, targetDate, and status")
        void shouldUpdateFields() {
            Goal goal = createGoal(50, GoalStatus.ACTIVE);
            Instant later = NOW.plusSeconds(60);
            Goal updated = goal.update("New title", "New desc",
                    LocalDate.of(2027, 1, 1), GoalStatus.COMPLETED, later);

            assertEquals("New title", updated.getTitle());
            assertEquals("New desc", updated.getDescription());
            assertEquals(LocalDate.of(2027, 1, 1), updated.getTargetDate());
            assertEquals(GoalStatus.COMPLETED, updated.getStatus());
            assertEquals(later, updated.getUpdatedAt());
            assertEquals(50, updated.getProgress()); // progress unchanged
        }
    }
}
