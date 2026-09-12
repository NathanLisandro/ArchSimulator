package dev.archsim.infrastructure.security;

import dev.archsim.domain.user.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Override public String hash(String raw) { return encoder.encode(raw); }
    @Override public boolean matches(String raw, String hash) { return raw != null && encoder.matches(raw, hash); }
}
