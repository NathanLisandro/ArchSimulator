package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity, String> {
    List<AttemptEntity> findByUserIdOrderByStartedAtDesc(String userId);
}
