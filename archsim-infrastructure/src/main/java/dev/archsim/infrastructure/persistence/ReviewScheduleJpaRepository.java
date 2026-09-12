package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewScheduleJpaRepository extends JpaRepository<ReviewScheduleEntity, ReviewScheduleEntity.Key> {
    List<ReviewScheduleEntity> findByUserId(String userId);
}
