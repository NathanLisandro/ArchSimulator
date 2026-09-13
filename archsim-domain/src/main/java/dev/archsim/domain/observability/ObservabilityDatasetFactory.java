package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.shared.RandomSource;

import java.time.Instant;
import java.util.List;

public final class ObservabilityDatasetFactory {
    private final List<ScenarioDatasetGenerator> generators;

    public ObservabilityDatasetFactory(List<ScenarioDatasetGenerator> generators) { this.generators = List.copyOf(generators); }

    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        return generate(theCase, company, random, null);
    }

    /** recoveredAt não nulo faz a telemetria se recuperar a partir daquele instante (mitigação aplicada). */
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random, Instant recoveredAt) {
        String kind = theCase.scenario().kind();
        ScenarioDatasetGenerator generator = generators.stream().filter(g -> g.kind().equals(kind)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dataset generator for scenario kind: " + kind));
        RandomSource forked = random.fork("dataset:" + kind);
        return recoveredAt == null
                ? generator.generate(theCase, company, forked)
                : generator.generate(theCase, company, forked, recoveredAt);
    }
}
