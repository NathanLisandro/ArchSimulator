package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.CaseRepository;

import java.util.Optional;

public class JpaCaseRepository implements CaseRepository {
    private final CaseJpaRepository cases;
    private final DomainJson json;
    public JpaCaseRepository(CaseJpaRepository cases, DomainJson json) { this.cases = cases; this.json = json; }

    @Override public void save(Case c) {
        cases.save(new CaseEntity(c.id().value(), c.companyId().value(), c.templateId(), c.type().name(), c.level().name(), c.seed(), c.generatedAt(), json.write(c)));
    }
    @Override public Optional<Case> findById(CaseId id) { return cases.findById(id.value()).map(e -> json.read(e.getDocument(), Case.class)); }
}
