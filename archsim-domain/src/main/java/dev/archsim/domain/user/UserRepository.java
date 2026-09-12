package dev.archsim.domain.user;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByUsername(String username);
}
