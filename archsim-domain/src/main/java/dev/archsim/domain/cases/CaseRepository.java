package dev.archsim.domain.cases;

import java.util.Optional;

public interface CaseRepository {
    void save(Case c);
    Optional<Case> findById(CaseId id);
}
