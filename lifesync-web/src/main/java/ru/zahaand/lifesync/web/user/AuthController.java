package ru.zahaand.lifesync.web.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.zahaand.lifesync.api.AuthApi;
import ru.zahaand.lifesync.api.model.LoginRequestDto;
import ru.zahaand.lifesync.api.model.LogoutRequestDto;
import ru.zahaand.lifesync.api.model.RefreshRequestDto;
import ru.zahaand.lifesync.api.model.RegisterRequestDto;
import ru.zahaand.lifesync.api.model.TokenResponseDto;
import ru.zahaand.lifesync.api.model.UserResponseDto;
import ru.zahaand.lifesync.application.user.LoginUserUseCase;
import ru.zahaand.lifesync.application.user.LogoutUserUseCase;
import ru.zahaand.lifesync.application.user.RefreshTokenUseCase;
import ru.zahaand.lifesync.application.user.RegisterUserUseCase;
import ru.zahaand.lifesync.domain.user.User;

@RestController
public class AuthController implements AuthApi {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUserUseCase logoutUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUserUseCase logoutUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUserUseCase = logoutUserUseCase;
    }

    @Override
    public ResponseEntity<UserResponseDto> register(RegisterRequestDto request) {
        User user = registerUserUseCase.execute(
                request.getEmail(),
                request.getUsername(),
                request.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserResponse(user));
    }

    @Override
    public ResponseEntity<TokenResponseDto> login(LoginRequestDto request) {
        LoginUserUseCase.LoginResult result = loginUserUseCase.execute(
                request.getIdentifier(),
                request.getPassword()
        );
        return ResponseEntity.ok(toTokenResponse(result.accessToken(),
                result.refreshToken(), result.expiresIn()));
    }

    @Override
    public ResponseEntity<TokenResponseDto> refreshToken(RefreshRequestDto request) {
        RefreshTokenUseCase.RefreshResult result = refreshTokenUseCase.execute(
                request.getRefreshToken()
        );
        return ResponseEntity.ok(toTokenResponse(result.accessToken(),
                result.refreshToken(), result.expiresIn()));
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequestDto request) {
        logoutUserUseCase.execute(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private UserResponseDto toUserResponse(User user) {
        return new UserResponseDto()
                .id(user.getId().value())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(UserResponseDto.RoleEnum.fromValue(user.getRole().name()))
                .createdAt(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
    }

    private TokenResponseDto toTokenResponse(String accessToken, String refreshToken, int expiresIn) {
        return new TokenResponseDto()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn);
    }
}
