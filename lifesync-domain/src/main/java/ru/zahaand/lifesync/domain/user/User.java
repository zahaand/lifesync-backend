package ru.zahaand.lifesync.domain.user;

import java.time.Instant;

public final class User {

    private final UserId id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;
    private final UserProfile profile;

    public User(UserId id, String email, String username, String passwordHash,
                Role role, boolean enabled, Instant createdAt, Instant updatedAt,
                Instant deletedAt, UserProfile profile) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.profile = profile;
    }

    public User ban() {
        return new User(id, email, username, passwordHash, role, false,
                createdAt, updatedAt, deletedAt, profile);
    }

    public User softDelete(Instant now) {
        return new User(id, email, username, passwordHash, role, enabled,
                createdAt, now, now, profile);
    }

    public User withProfile(UserProfile profile) {
        return new User(id, email, username, passwordHash, role, enabled,
                createdAt, updatedAt, deletedAt, profile);
    }

    public User withUpdatedAt(Instant updatedAt) {
        return new User(id, email, username, passwordHash, role, enabled,
                createdAt, updatedAt, deletedAt, profile);
    }

    public boolean isActive() {
        return enabled && deletedAt == null;
    }

    public boolean isBanned() {
        return !enabled;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UserId getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UserProfile getProfile() {
        return profile;
    }
}
