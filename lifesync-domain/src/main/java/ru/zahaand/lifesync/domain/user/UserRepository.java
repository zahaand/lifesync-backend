package ru.zahaand.lifesync.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    User save(User user);

    User update(User user);

    UserPage findAll(String status, String search, int page, int size);

    record UserPage(List<User> content, long totalElements, int totalPages, int page, int size) {
    }
}
