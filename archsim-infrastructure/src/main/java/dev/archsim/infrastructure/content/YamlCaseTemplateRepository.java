package dev.archsim.infrastructure.content;

import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.CaseTemplateRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YamlCaseTemplateRepository implements CaseTemplateRepository {
    private final List<CaseTemplate> templates = new ArrayList<>();

    public YamlCaseTemplateRepository(Path contentRoot) {
        TemplateMapper mapper = new TemplateMapper();
        for (YamlContentLoader.Loaded l : new YamlContentLoader().loadAll(contentRoot.resolve("templates"))) {
            templates.add(mapper.map(l.node(), l.path().getFileName().toString()));
        }
    }

    @Override public List<CaseTemplate> findAll() { return List.copyOf(templates); }
    @Override public Optional<CaseTemplate> findById(String id) { return templates.stream().filter(t -> t.id().equals(id)).findFirst(); }
}
