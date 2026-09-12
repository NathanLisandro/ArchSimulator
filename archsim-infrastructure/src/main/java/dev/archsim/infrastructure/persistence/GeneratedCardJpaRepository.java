package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedCardJpaRepository extends JpaRepository<GeneratedCardEntity, GeneratedCardEntity.Key> {
    List<GeneratedCardEntity> findByUserId(String userId);
}
