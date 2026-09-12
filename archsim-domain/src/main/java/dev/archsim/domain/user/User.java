package dev.archsim.domain.user;

import java.time.Instant;

/** Aggregate root: local account (no OAuth). Password is stored hashed by the PasswordHasher port. */
public record User(UserId id, String username, String displayName, String passwordHash, Instant createdAt) {
    public User {
        if (username == null || !username.matches("[a-z0-9._-]{3,32}")) throw new IllegalArgumentException("username must be 3-32 chars: a-z 0-9 . _ -");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
    }

    public static User register(UserId id, String username, String displayName, String rawPassword, PasswordHasher hasher, Instant now) {
        if (rawPassword == null || rawPassword.length() < 6) throw new IllegalArgumentException("password must have at least 6 characters");
        return new User(id, username.toLowerCase(), displayName.trim(), hasher.hash(rawPassword), now);
    }

    public boolean passwordMatches(String rawPassword, PasswordHasher hasher) { return hasher.matches(rawPassword, passwordHash); }
}
