package ru.zahaand.lifesync.application.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.lifesync.domain.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdminUsersUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private GetAdminUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAdminUsersUseCase(userRepository);
    }

    @Nested
    class Execute {

        @Test
        @DisplayName("Should return paginated users without filters")
        void shouldReturnPaginatedUsers() {
            UserRepository.UserPage page = new UserRepository.UserPage(List.of(), 0, 0, 0, 20);
            when(userRepository.findAll(null, null, 0, 20)).thenReturn(page);

            UserRepository.UserPage result = useCase.execute(null, null, 0, 20);

            assertThat(result.totalElements()).isZero();
            verify(userRepository).findAll(null, null, 0, 20);
        }

        @Test
        @DisplayName("Should filter by active status")
        void shouldFilterByActiveStatus() {
            UserRepository.UserPage page = new UserRepository.UserPage(List.of(), 0, 0, 0, 20);
            when(userRepository.findAll("active", null, 0, 20)).thenReturn(page);

            useCase.execute("active", null, 0, 20);

            verify(userRepository).findAll("active", null, 0, 20);
        }

        @Test
        @DisplayName("Should search by email")
        void shouldSearchByEmail() {
            UserRepository.UserPage page = new UserRepository.UserPage(List.of(), 0, 0, 0, 20);
            when(userRepository.findAll(null, "user@", 0, 20)).thenReturn(page);

            useCase.execute(null, "user@", 0, 20);

            verify(userRepository).findAll(null, "user@", 0, 20);
        }

        @Test
        @DisplayName("Should cap page size at 100")
        void shouldCapPageSize() {
            UserRepository.UserPage page = new UserRepository.UserPage(List.of(), 0, 0, 0, 100);
            when(userRepository.findAll(null, null, 0, 100)).thenReturn(page);

            useCase.execute(null, null, 0, 200);

            verify(userRepository).findAll(null, null, 0, 100);
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void shouldHandlePagination() {
            UserRepository.UserPage page = new UserRepository.UserPage(List.of(), 50, 3, 1, 20);
            when(userRepository.findAll(null, null, 1, 20)).thenReturn(page);

            UserRepository.UserPage result = useCase.execute(null, null, 1, 20);

            assertThat(result.page()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(50);
        }
    }
}
