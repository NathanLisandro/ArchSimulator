package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.user.User;
import dev.archsim.domain.user.UserId;
import dev.archsim.domain.user.UserRepository;

import java.util.Optional;

public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository users;
    public JpaUserRepository(UserJpaRepository users) { this.users = users; }

    @Override public void save(User u) { users.save(new UserEntity(u.id().value(), u.username(), u.displayName(), u.passwordHash(), u.createdAt())); }
    @Override public Optional<User> findById(UserId id) { return users.findById(id.value()).map(this::toDomain); }
    @Override public Optional<User> findByUsername(String username) { return users.findByUsername(username).map(this::toDomain); }

    private User toDomain(UserEntity e) { return new User(new UserId(e.getId()), e.getUsername(), e.getDisplayName(), e.getPasswordHash(), e.getCreatedAt()); }
}
