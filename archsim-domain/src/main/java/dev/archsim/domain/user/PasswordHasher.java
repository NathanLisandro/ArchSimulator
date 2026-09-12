package dev.archsim.domain.user;

public interface PasswordHasher {
    String hash(String raw);
    boolean matches(String raw, String hash);
}
