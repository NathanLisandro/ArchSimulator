package dev.archsim.domain.observability;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.fixtures.Fixtures;
import dev.archsim.domain.rubric.Rubric;
import dev.archsim.domain.shared.SeededRandom;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityDatasetGeneratorTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    private Case capacityCase() {
        SolutionPath p = new SolutionPath("shard", "Sharding", "s", List.of(), List.of(), "w", new Rubric(List.of()));
        return new Case(CaseId.of("c"), new CompanyId("banco-digital"), "t", CaseType.CAPACITY_PLANNING, Level.SENIOR, 3L,
                new Narrative("t", "s", "b", List.of()), List.of(), List.of(p), new AnswerForm(List.of(), "solutionPath"),
                new ReferenceAnswer(""), new ScenarioSpec("capacity", Map.of("service", "account-api", "datastore", "core-db",
                        "growthPercentPerMonth", "9", "currentPeakRps", "1200", "headroomMetric", "active_connections",
                        "headroomLimit", "5000", "headroomNow", "3400")),
                new PathRecommendation("shard", "r"), List.of("capacity"), Set.of(ConsoleTool.METRICS), Map.of(), NOW);
    }

    private final ObservabilityDatasetFactory factory = new ObservabilityDatasetFactory(List.of(new CapacityDatasetGenerator(), new EmptyDatasetGenerator()));

    @Test
    void showsNinetyDaysOfGrowthWithAResourceApproachingItsLimit() {
        ObservabilityDataset ds = factory.generate(capacityCase(), Fixtures.bank(), new SeededRandom(3L));

        assertThat(ds.window().duration()).isEqualTo(Duration.ofDays(90));
        MetricSeries rps = ds.series("account-api", "requests_per_second").orElseThrow();
        assertThat(rps.points()).hasSizeGreaterThan(2000);
        double first = avg(rps, ds.window().from(), ds.window().from().plus(Duration.ofDays(7)));
        double last = avg(rps, ds.window().to().minus(Duration.ofDays(7)), ds.window().to());
        assertThat(last).isGreaterThan(first * 1.15);

        MetricSeries conns = ds.series("core-db", "active_connections").orElseThrow();
        assertThat(conns.last()).isBetween(3000.0, 3800.0);
        assertThat(ds.series("core-db", "active_connections_limit").orElseThrow().last()).isEqualTo(5000);
        assertThat(ds.series("account-api", "error_rate").orElseThrow().max()).isLessThan(1.5);
        assertThat(ds.services()).contains("pix-api", "receivables-api");
        assertThat(ds.logs()).isEmpty();
    }

    @Test
    void emptyScenarioProducesNoTelemetryButKeepsTheSchema() {
        Case c = new Case(CaseId.of("c"), new CompanyId("banco-digital"), "t", CaseType.ADR, Level.SENIOR, 1L,
                new Narrative("t", "s", "b", List.of()), List.of(),
                List.of(new SolutionPath("p", "P", "s", List.of(), List.of(), "w", new Rubric(List.of()))),
                new AnswerForm(List.of(), "solutionPath"), new ReferenceAnswer(""), new ScenarioSpec("none", Map.of()),
                new PathRecommendation("p", "r"), List.of("adr"), Set.of(), Map.of(), NOW);
        ObservabilityDataset ds = factory.generate(c, Fixtures.bank(), new SeededRandom(1L));
        assertThat(ds.metrics()).isEmpty();
        assertThat(ds.dbState().effectiveSchema().tables()).isNotEmpty();
    }

    private static double avg(MetricSeries s, Instant from, Instant to) {
        return s.points().stream().filter(p -> !p.at().isBefore(from) && !p.at().isAfter(to)).mapToDouble(MetricPoint::value).average().orElseThrow();
    }
}
