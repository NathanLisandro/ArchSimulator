package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.shared.RandomSource;

import java.time.Instant;

/** Builds the console dataset for one scenario kind (slow-query, incident, capacity...). Must be deterministic per random source. */
public interface ScenarioDatasetGenerator {
    String kind();

    ObservabilityDataset generate(Case theCase, Company company, RandomSource random);

    /**
     * Same dataset, but reflecting that the responder mitigated the incident at {@code recoveredAt}: the telemetry
     * recovers from that instant instead of staying degraded until the end of the window.
     */
    default ObservabilityDataset generate(Case theCase, Company company, RandomSource random, Instant recoveredAt) {
        return generate(theCase, company, random);
    }
}
