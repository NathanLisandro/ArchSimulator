package dev.archsim.application.usecase;

import dev.archsim.application.dto.UserView;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.PasswordHasher;
import dev.archsim.domain.user.User;
import dev.archsim.domain.user.UserId;
import dev.archsim.domain.user.UserRepository;

import java.util.UUID;

public class RegisterUser {
    public record Command(String username, String displayName, String password) {}

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUser(UserRepository users, PasswordHasher hasher, Clock clock) { this.users = users; this.hasher = hasher; this.clock = clock; }

    public UserView execute(Command cmd) {
        String username = cmd.username() == null ? "" : cmd.username().trim().toLowerCase();
        if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("Usuário já existe: " + username);
        User user = User.register(new UserId(UUID.randomUUID().toString()), username, cmd.displayName(), cmd.password(), hasher, clock.now());
        users.save(user);
        return UserView.from(user);
    }
}
