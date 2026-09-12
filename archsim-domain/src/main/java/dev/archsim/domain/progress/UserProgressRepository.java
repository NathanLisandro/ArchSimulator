package dev.archsim.domain.progress;

import dev.archsim.domain.user.UserId;

import java.util.Optional;

public interface UserProgressRepository {
    void save(UserProgress progress);
    Optional<UserProgress> findByUser(UserId userId);
}
