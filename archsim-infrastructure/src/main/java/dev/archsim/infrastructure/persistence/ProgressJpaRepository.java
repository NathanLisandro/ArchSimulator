package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressJpaRepository extends JpaRepository<UserProgressEntity, String> {}
