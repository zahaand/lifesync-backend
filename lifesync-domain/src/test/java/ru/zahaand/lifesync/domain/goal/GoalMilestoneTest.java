package ru.zahaand.lifesync.domain.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoalMilestoneTest {

    private static final GoalMilestoneId MILESTONE_ID = new GoalMilestoneId(UUID.randomUUID());
    private static final GoalId GOAL_ID = new GoalId(UUID.randomUUID());
    private static final Instant NOW = Instant.now();

    private GoalMilestone createMilestone() {
        return new GoalMilestone(MILESTONE_ID, GOAL_ID, "Step 1", 0,
                false, null, NOW, NOW, null);
    }

    @Nested
    class Complete {

        @Test
        @DisplayName("Should set completed to true and completedAt")
        void shouldComplete() {
            GoalMilestone m = createMilestone();
            Instant later = NOW.plusSeconds(60);
            GoalMilestone completed = m.complete(later);

            assertTrue(completed.getCompleted());
            assertEquals(later, completed.getCompletedAt());
        }
    }

    @Nested
    class Uncomplete {

        @Test
        @DisplayName("Should set completed to false and clear completedAt")
        void shouldUncomplete() {
            GoalMilestone m = createMilestone().complete(NOW.plusSeconds(30));
            GoalMilestone uncompleted = m.uncomplete(NOW.plusSeconds(60));

            assertFalse(uncompleted.getCompleted());
            assertNull(uncompleted.getCompletedAt());
        }
    }

    @Nested
    class UpdateMethod {

        @Test
        @DisplayName("Should update title and sortOrder")
        void shouldUpdateFields() {
            GoalMilestone m = createMilestone();
            Instant later = NOW.plusSeconds(60);
            GoalMilestone updated = m.update("New title", 5, later);

            assertEquals("New title", updated.getTitle());
            assertEquals(5, updated.getSortOrder());
            assertEquals(later, updated.getUpdatedAt());
        }
    }

    @Nested
    class SoftDelete {

        @Test
        @DisplayName("Should set deletedAt")
        void shouldSoftDelete() {
            GoalMilestone m = createMilestone();
            Instant later = NOW.plusSeconds(60);
            GoalMilestone deleted = m.softDelete(later);

            assertTrue(deleted.isDeleted());
            assertEquals(later, deleted.getDeletedAt());
        }
    }
}
