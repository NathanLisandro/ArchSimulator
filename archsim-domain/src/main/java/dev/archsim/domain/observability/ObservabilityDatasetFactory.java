package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.shared.RandomSource;

import java.util.List;

public final class ObservabilityDatasetFactory {
    private final List<ScenarioDatasetGenerator> generators;

    public ObservabilityDatasetFactory(List<ScenarioDatasetGenerator> generators) { this.generators = List.copyOf(generators); }

    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        String kind = theCase.scenario().kind();
        return generators.stream().filter(g -> g.kind().equals(kind)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dataset generator for scenario kind: " + kind))
                .generate(theCase, company, random.fork("dataset:" + kind));
    }
}
