package dev.archsim.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseJpaRepository extends JpaRepository<CaseEntity, String> {}
