package ru.zahaand.lifesync.web.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.zahaand.lifesync.api.UserApi;
import ru.zahaand.lifesync.api.model.ConnectTelegramRequestDto;
import ru.zahaand.lifesync.api.model.UpdateProfileRequestDto;
import ru.zahaand.lifesync.api.model.UserProfileResponseDto;
import ru.zahaand.lifesync.application.user.ConnectTelegramUseCase;
import ru.zahaand.lifesync.application.user.DeleteUserUseCase;
import ru.zahaand.lifesync.application.user.GetUserProfileUseCase;
import ru.zahaand.lifesync.application.user.UpdateUserProfileUseCase;
import ru.zahaand.lifesync.domain.user.TokenProvider;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;

import java.time.ZoneOffset;

@RestController
public class UserController implements UserApi {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final ConnectTelegramUseCase connectTelegramUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public UserController(GetUserProfileUseCase getUserProfileUseCase,
                          UpdateUserProfileUseCase updateUserProfileUseCase,
                          ConnectTelegramUseCase connectTelegramUseCase,
                          DeleteUserUseCase deleteUserUseCase) {
        this.getUserProfileUseCase = getUserProfileUseCase;
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        this.connectTelegramUseCase = connectTelegramUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
    }

    @Override
    public ResponseEntity<UserProfileResponseDto> getMyProfile() {
        UserId userId = getCurrentUserId();
        User user = getUserProfileUseCase.execute(userId);
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @Override
    public ResponseEntity<UserProfileResponseDto> updateMyProfile(UpdateProfileRequestDto request) {
        UserId userId = getCurrentUserId();
        User user = updateUserProfileUseCase.execute(
                userId,
                request.getDisplayName(),
                request.getTimezone(),
                request.getLocale()
        );
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @Override
    public ResponseEntity<UserProfileResponseDto> connectTelegram(ConnectTelegramRequestDto request) {
        UserId userId = getCurrentUserId();
        User user = connectTelegramUseCase.execute(userId, request.getTelegramChatId());
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @Override
    public ResponseEntity<Void> deleteMyAccount() {
        UserId userId = getCurrentUserId();
        deleteUserUseCase.execute(userId);
        return ResponseEntity.noContent().build();
    }

    private UserId getCurrentUserId() {
        TokenProvider.TokenClaims claims = (TokenProvider.TokenClaims)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return claims.userId();
    }

    private UserProfileResponseDto toProfileResponse(User user) {
        return new UserProfileResponseDto()
                .id(user.getId().value())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(UserProfileResponseDto.RoleEnum.fromValue(user.getRole().name()))
                .displayName(user.getProfile().displayName())
                .timezone(user.getProfile().timezone())
                .locale(user.getProfile().locale())
                .telegramChatId(user.getProfile().telegramChatId())
                .createdAt(user.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
