package ru.zahaand.lifesync.application.user;

import ru.zahaand.lifesync.domain.user.UserRepository;

public class GetAdminUsersUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    public GetAdminUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserRepository.UserPage execute(String status, String search, int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        return userRepository.findAll(status, search, effectivePage, effectiveSize);
    }
}
