package dev.archsim.domain.attempt;

import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository {
    void save(Attempt attempt);
    Optional<Attempt> findById(AttemptId id);
    List<Attempt> findByUser(UserId userId);
}
