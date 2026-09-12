package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Duration;
import java.util.List;

/** Scenario "none": case types that are answered from the briefing alone (ADR, trade-off, code review). */
public final class EmptyDatasetGenerator implements ScenarioDatasetGenerator {
    @Override public String kind() { return "none"; }

    @Override
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        TimeWindow window = new TimeWindow(theCase.generatedAt().minus(Duration.ofHours(1)), theCase.generatedAt());
        return new ObservabilityDataset(window, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new DbState(List.of(), List.of(), List.of(), List.of(), company.schema()));
    }
}
