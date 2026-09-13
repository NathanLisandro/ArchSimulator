package dev.archsim.infrastructure.config;

import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.observability.ObservabilityDatasetFactory;
import dev.archsim.domain.shared.SeededRandom;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Regenerates datasets from the case seed (ADR-0002) and keeps the most recent ones in memory. */
public class CachingDatasetProvider implements ObservabilityDatasetProvider {
    private static final int MAX = 32;
    private final ObservabilityDatasetFactory factory;
    private final CompanyRepository companies;
    private final Map<String, ObservabilityDataset> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ObservabilityDataset> e) { return size() > MAX; }
    };

    public CachingDatasetProvider(ObservabilityDatasetFactory factory, CompanyRepository companies) { this.factory = factory; this.companies = companies; }

    /** A chave inclui o instante da mitigação: antes e depois dela o cenário é outro. */
    @Override
    public synchronized ObservabilityDataset datasetFor(Case c, Instant recoveredAt) {
        String key = c.id().value() + "@" + (recoveredAt == null ? "open" : recoveredAt.toString());
        return cache.computeIfAbsent(key, k ->
                factory.generate(c, companies.findById(c.companyId()).orElseThrow(), new SeededRandom(c.seed()), recoveredAt));
    }
}
