package dev.archsim.infrastructure.content;

import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.company.CompanyRepository;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class YamlCompanyRepository implements CompanyRepository {
    private final Map<CompanyId, Company> companies = new LinkedHashMap<>();

    public YamlCompanyRepository(Path contentRoot) {
        CompanyMapper mapper = new CompanyMapper();
        for (YamlContentLoader.Loaded l : new YamlContentLoader().loadAll(contentRoot.resolve("companies"))) {
            Company c = mapper.map(l.node(), l.path().getFileName().toString());
            companies.put(c.id(), c);
        }
    }

    @Override public Optional<Company> findById(CompanyId id) { return Optional.ofNullable(companies.get(id)); }
    @Override public List<Company> findAll() { return List.copyOf(companies.values()); }
}
