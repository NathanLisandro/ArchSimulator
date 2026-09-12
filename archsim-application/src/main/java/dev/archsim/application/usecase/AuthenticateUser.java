package dev.archsim.application.usecase;

import dev.archsim.application.dto.UserView;
import dev.archsim.domain.user.PasswordHasher;
import dev.archsim.domain.user.UserRepository;

import java.util.Optional;

public class AuthenticateUser {
    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthenticateUser(UserRepository users, PasswordHasher hasher) { this.users = users; this.hasher = hasher; }

    public Optional<UserView> execute(String username, String password) {
        return users.findByUsername(username == null ? "" : username.trim().toLowerCase())
                .filter(u -> u.passwordMatches(password, hasher)).map(UserView::from);
    }
}
