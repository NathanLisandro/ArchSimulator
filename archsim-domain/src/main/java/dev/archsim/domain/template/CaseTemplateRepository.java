package dev.archsim.domain.template;

import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.company.CompanyId;

import java.util.List;
import java.util.Optional;

public interface CaseTemplateRepository {
    List<CaseTemplate> findAll();
    Optional<CaseTemplate> findById(String id);

    default List<CaseTemplate> find(CompanyId companyId, CaseType type, Level level) {
        return findAll().stream()
                .filter(t -> companyId == null || t.companyId().equals(companyId))
                .filter(t -> type == null || t.type() == type)
                .filter(t -> level == null || t.supports(level))
                .toList();
    }
}
