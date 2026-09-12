package dev.archsim.domain.sprint;

import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface SprintRepository {
    void save(Sprint sprint);
    Optional<Sprint> findById(SprintId id);
    List<Sprint> findByUser(UserId userId);
    Optional<Sprint> findRunning(UserId userId);
}
