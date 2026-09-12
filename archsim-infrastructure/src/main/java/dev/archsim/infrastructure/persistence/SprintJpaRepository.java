package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintJpaRepository extends JpaRepository<SprintEntity, String> {
    List<SprintEntity> findByUserIdOrderByStartedAtDesc(String userId);
    Optional<SprintEntity> findFirstByUserIdAndStatus(String userId, String status);
}
