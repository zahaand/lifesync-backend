package ru.zahaand.lifesync.application.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateGoalUseCaseTest {

    @Mock
    private GoalRepository goalRepository;

    private CreateGoalUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        useCase = new CreateGoalUseCase(goalRepository, CLOCK);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should create goal with ACTIVE status and 0 progress")
        void shouldCreateGoalWithDefaults() {
            when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

            Goal result = useCase.execute(USER_ID, "Learn Java", "desc", LocalDate.of(2026, 12, 31));

            assertNotNull(result);
            assertEquals("Learn Java", result.getTitle());
            assertEquals("desc", result.getDescription());
            assertEquals(GoalStatus.ACTIVE, result.getStatus());
            assertEquals(0, result.getProgress());
            verify(goalRepository).save(any(Goal.class));
        }

        @Test
        @DisplayName("Should create goal without target date")
        void shouldCreateWithoutTargetDate() {
            when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

            Goal result = useCase.execute(USER_ID, "Open-ended", null, null);

            assertNull(result.getTargetDate());
        }
    }
}
