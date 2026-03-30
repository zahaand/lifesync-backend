package ru.zahaand.lifesync.web.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.zahaand.lifesync.api.AdminApi;
import ru.zahaand.lifesync.api.model.AdminUserResponseDto;
import ru.zahaand.lifesync.api.model.UserPageResponseDto;
import ru.zahaand.lifesync.application.user.BanUserUseCase;
import ru.zahaand.lifesync.application.user.GetAdminUserUseCase;
import ru.zahaand.lifesync.application.user.GetAdminUsersUseCase;
import ru.zahaand.lifesync.domain.user.User;
import ru.zahaand.lifesync.domain.user.UserId;
import ru.zahaand.lifesync.domain.user.UserRepository;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
public class AdminController implements AdminApi {

    private final GetAdminUsersUseCase getAdminUsersUseCase;
    private final GetAdminUserUseCase getAdminUserUseCase;
    private final BanUserUseCase banUserUseCase;

    public AdminController(GetAdminUsersUseCase getAdminUsersUseCase,
                           GetAdminUserUseCase getAdminUserUseCase,
                           BanUserUseCase banUserUseCase) {
        this.getAdminUsersUseCase = getAdminUsersUseCase;
        this.getAdminUserUseCase = getAdminUserUseCase;
        this.banUserUseCase = banUserUseCase;
    }

    @Override
    public ResponseEntity<UserPageResponseDto> listUsers(String status, String search,
                                                         Integer page, Integer size) {
        UserRepository.UserPage userPage = getAdminUsersUseCase.execute(status, search, page, size);

        UserPageResponseDto response = new UserPageResponseDto()
                .content(userPage.content().stream().map(this::toAdminUserResponse).toList())
                .totalElements(userPage.totalElements())
                .totalPages(userPage.totalPages())
                .page(userPage.page())
                .size(userPage.size());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AdminUserResponseDto> getUserById(UUID id) {
        User user = getAdminUserUseCase.execute(new UserId(id));
        return ResponseEntity.ok(toAdminUserResponse(user));
    }

    @Override
    public ResponseEntity<AdminUserResponseDto> banUser(UUID id) {
        User user = banUserUseCase.execute(new UserId(id));
        return ResponseEntity.ok(toAdminUserResponse(user));
    }

    private AdminUserResponseDto toAdminUserResponse(User user) {
        AdminUserResponseDto dto = new AdminUserResponseDto()
                .id(user.getId().value())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(AdminUserResponseDto.RoleEnum.fromValue(user.getRole().name()))
                .enabled(user.isEnabled())
                .displayName(user.getProfile().displayName())
                .timezone(user.getProfile().timezone())
                .locale(user.getProfile().locale())
                .telegramChatId(user.getProfile().telegramChatId())
                .createdAt(user.getCreatedAt().atOffset(ZoneOffset.UTC));

        if (user.getDeletedAt() != null) {
            dto.deletedAt(user.getDeletedAt().atOffset(ZoneOffset.UTC));
        }
        return dto;
    }
}
