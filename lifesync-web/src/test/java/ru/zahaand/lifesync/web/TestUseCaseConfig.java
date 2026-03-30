package ru.zahaand.lifesync.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.zahaand.lifesync.application.habit.CompleteHabitUseCase;
import ru.zahaand.lifesync.application.habit.CreateHabitUseCase;
import ru.zahaand.lifesync.application.habit.DeleteHabitLogUseCase;
import ru.zahaand.lifesync.application.habit.DeleteHabitUseCase;
import ru.zahaand.lifesync.application.habit.GetHabitLogsUseCase;
import ru.zahaand.lifesync.application.habit.GetHabitStreakUseCase;
import ru.zahaand.lifesync.application.habit.GetHabitUseCase;
import ru.zahaand.lifesync.application.habit.GetHabitsUseCase;
import ru.zahaand.lifesync.application.habit.StreakCalculatorService;
import ru.zahaand.lifesync.application.habit.UpdateHabitUseCase;
import ru.zahaand.lifesync.application.user.BanUserUseCase;
import ru.zahaand.lifesync.application.user.ConnectTelegramUseCase;
import ru.zahaand.lifesync.application.user.DeleteUserUseCase;
import ru.zahaand.lifesync.application.user.GetAdminUserUseCase;
import ru.zahaand.lifesync.application.user.GetAdminUsersUseCase;
import ru.zahaand.lifesync.application.user.GetUserProfileUseCase;
import ru.zahaand.lifesync.application.user.LoginUserUseCase;
import ru.zahaand.lifesync.application.user.LogoutUserUseCase;
import ru.zahaand.lifesync.application.user.RefreshTokenUseCase;
import ru.zahaand.lifesync.application.user.RegisterUserUseCase;
import ru.zahaand.lifesync.application.user.UpdateUserProfileUseCase;
import ru.zahaand.lifesync.domain.habit.HabitLogRepository;
import ru.zahaand.lifesync.domain.habit.HabitRepository;
import ru.zahaand.lifesync.domain.habit.HabitStreakRepository;
import ru.zahaand.lifesync.domain.user.PasswordEncoder;
import ru.zahaand.lifesync.domain.user.RefreshTokenRepository;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.UserRepository;

import java.time.Clock;

@Configuration
public class TestUseCaseConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepository userRepository,
                                                   PasswordEncoder passwordEncoder,
                                                   Clock clock) {
        return new RegisterUserUseCase(userRepository, passwordEncoder, clock);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(UserRepository userRepository,
                                             PasswordEncoder passwordEncoder,
                                             TokenProvider tokenProvider,
                                             RefreshTokenRepository refreshTokenRepository,
                                             Clock clock,
                                             @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
                                             @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        return new LoginUserUseCase(userRepository, passwordEncoder, tokenProvider,
                refreshTokenRepository, clock, accessTokenExpiry, refreshTokenExpiry);
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(RefreshTokenRepository refreshTokenRepository,
                                                   UserRepository userRepository,
                                                   TokenProvider tokenProvider,
                                                   Clock clock,
                                                   @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
                                                   @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        return new RefreshTokenUseCase(refreshTokenRepository, userRepository, tokenProvider,
                clock, accessTokenExpiry, refreshTokenExpiry);
    }

    @Bean
    public LogoutUserUseCase logoutUserUseCase(RefreshTokenRepository refreshTokenRepository) {
        return new LogoutUserUseCase(refreshTokenRepository);
    }

    @Bean
    public GetUserProfileUseCase getUserProfileUseCase(UserRepository userRepository) {
        return new GetUserProfileUseCase(userRepository);
    }

    @Bean
    public UpdateUserProfileUseCase updateUserProfileUseCase(UserRepository userRepository,
                                                             Clock clock) {
        return new UpdateUserProfileUseCase(userRepository, clock);
    }

    @Bean
    public ConnectTelegramUseCase connectTelegramUseCase(UserRepository userRepository,
                                                        Clock clock) {
        return new ConnectTelegramUseCase(userRepository, clock);
    }

    @Bean
    public DeleteUserUseCase deleteUserUseCase(UserRepository userRepository,
                                               RefreshTokenRepository refreshTokenRepository,
                                               Clock clock) {
        return new DeleteUserUseCase(userRepository, refreshTokenRepository, clock);
    }

    @Bean
    public GetAdminUsersUseCase getAdminUsersUseCase(UserRepository userRepository) {
        return new GetAdminUsersUseCase(userRepository);
    }

    @Bean
    public GetAdminUserUseCase getAdminUserUseCase(UserRepository userRepository) {
        return new GetAdminUserUseCase(userRepository);
    }

    @Bean
    public BanUserUseCase banUserUseCase(UserRepository userRepository,
                                         RefreshTokenRepository refreshTokenRepository,
                                         Clock clock) {
        return new BanUserUseCase(userRepository, refreshTokenRepository, clock);
    }

    @Bean
    public StreakCalculatorService streakCalculatorService(Clock clock) {
        return new StreakCalculatorService(clock);
    }

    @Bean
    public CreateHabitUseCase createHabitUseCase(HabitRepository habitRepository,
                                                  HabitStreakRepository habitStreakRepository,
                                                  Clock clock) {
        return new CreateHabitUseCase(habitRepository, habitStreakRepository, clock);
    }

    @Bean
    public GetHabitsUseCase getHabitsUseCase(HabitRepository habitRepository) {
        return new GetHabitsUseCase(habitRepository);
    }

    @Bean
    public GetHabitUseCase getHabitUseCase(HabitRepository habitRepository) {
        return new GetHabitUseCase(habitRepository);
    }

    @Bean
    public UpdateHabitUseCase updateHabitUseCase(HabitRepository habitRepository,
                                                  HabitLogRepository habitLogRepository,
                                                  HabitStreakRepository habitStreakRepository,
                                                  StreakCalculatorService streakCalculatorService,
                                                  Clock clock) {
        return new UpdateHabitUseCase(habitRepository, habitLogRepository,
                habitStreakRepository, streakCalculatorService, clock);
    }

    @Bean
    public DeleteHabitUseCase deleteHabitUseCase(HabitRepository habitRepository,
                                                  Clock clock) {
        return new DeleteHabitUseCase(habitRepository, clock);
    }

    @Bean
    public CompleteHabitUseCase completeHabitUseCase(HabitRepository habitRepository,
                                                      HabitLogRepository habitLogRepository,
                                                      HabitStreakRepository habitStreakRepository,
                                                      StreakCalculatorService streakCalculatorService,
                                                      Clock clock) {
        return new CompleteHabitUseCase(habitRepository, habitLogRepository,
                habitStreakRepository, streakCalculatorService, clock);
    }

    @Bean
    public DeleteHabitLogUseCase deleteHabitLogUseCase(HabitRepository habitRepository,
                                                        HabitLogRepository habitLogRepository,
                                                        HabitStreakRepository habitStreakRepository,
                                                        StreakCalculatorService streakCalculatorService,
                                                        Clock clock) {
        return new DeleteHabitLogUseCase(habitRepository, habitLogRepository,
                habitStreakRepository, streakCalculatorService, clock);
    }

    @Bean
    public GetHabitLogsUseCase getHabitLogsUseCase(HabitRepository habitRepository,
                                                    HabitLogRepository habitLogRepository) {
        return new GetHabitLogsUseCase(habitRepository, habitLogRepository);
    }

    @Bean
    public GetHabitStreakUseCase getHabitStreakUseCase(HabitRepository habitRepository,
                                                       HabitStreakRepository habitStreakRepository) {
        return new GetHabitStreakUseCase(habitRepository, habitStreakRepository);
    }
}
