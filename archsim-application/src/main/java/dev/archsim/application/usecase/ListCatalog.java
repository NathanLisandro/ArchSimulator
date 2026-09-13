package dev.archsim.application.usecase;

import dev.archsim.application.dto.CatalogEntry;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.CaseTemplateRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diz quais combinações de empresa, tipo e nível têm template. A matriz é esparsa de propósito. */
public class ListCatalog {
    private final CaseTemplateRepository templates;
    public ListCatalog(CaseTemplateRepository templates) { this.templates = templates; }

    public List<CatalogEntry> all() {
        record Key(String companyId, CaseType caseType) {}
        Map<Key, List<CaseTemplate>> grouped = new LinkedHashMap<>();
        for (CaseTemplate template : templates.findAll()) {
            grouped.computeIfAbsent(new Key(template.companyId().value(), template.type()), k -> new ArrayList<>()).add(template);
        }
        return grouped.entrySet().stream()
                .map(e -> {
                    EnumSet<Level> levels = EnumSet.noneOf(Level.class);
                    e.getValue().forEach(t -> levels.addAll(t.levels()));
                    return new CatalogEntry(e.getKey().companyId(), e.getKey().caseType(), List.copyOf(levels), e.getValue().size());
                })
                .sorted(Comparator.comparing(CatalogEntry::companyId).thenComparing(CatalogEntry::caseType))
                .toList();
    }
}
