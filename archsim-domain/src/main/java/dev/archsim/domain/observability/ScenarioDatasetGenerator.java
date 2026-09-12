package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.shared.RandomSource;

/** Builds the console dataset for one scenario kind (slow-query, incident, capacity...). Must be deterministic per random source. */
public interface ScenarioDatasetGenerator {
    String kind();
    ObservabilityDataset generate(Case theCase, Company company, RandomSource random);
}
