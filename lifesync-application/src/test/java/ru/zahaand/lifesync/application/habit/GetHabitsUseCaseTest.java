package ru.zahaand.lifesync.application.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.habit.HabitRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHabitsUseCaseTest {

    @Mock
    private HabitRepository habitRepository;

    private GetHabitsUseCase useCase;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetHabitsUseCase(habitRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated habit list")
        void shouldReturnPaginatedList() {
            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(), 0, 0, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, "active", 0, 20)).thenReturn(page);

            HabitRepository.HabitPage result = useCase.execute(USER_ID, "active", 0, 20);

            assertEquals(0, result.totalElements());
            verify(habitRepository).findAllByUserId(USER_ID, "active", 0, 20);
        }

        @Test
        @DisplayName("Should pass userId to repository")
        void shouldPassUserId() {
            HabitRepository.HabitPage page = new HabitRepository.HabitPage(List.of(), 0, 0, 0, 20);
            when(habitRepository.findAllByUserId(USER_ID, null, 0, 20)).thenReturn(page);

            useCase.execute(USER_ID, null, 0, 20);

            verify(habitRepository).findAllByUserId(USER_ID, null, 0, 20);
        }
    }
}
