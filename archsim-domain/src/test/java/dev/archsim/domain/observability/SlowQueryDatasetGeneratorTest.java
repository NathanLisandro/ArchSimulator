package dev.archsim.domain.observability;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.fixtures.Fixtures;
import dev.archsim.domain.rubric.Rubric;
import dev.archsim.domain.shared.SeededRandom;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlowQueryDatasetGeneratorTest {
    private final Instant now = Instant.parse("2026-09-12T15:00:00Z");
    private final Instant since = Instant.parse("2026-09-12T03:40:00Z");
    private final Company bank = Fixtures.bank();

    private Case slowQueryCase() {
        SolutionPath p = new SolutionPath("composite-index", "Índice", "s", List.of(), List.of(), "w", new Rubric(List.of()));
        return new Case(CaseId.of("c1"), new CompanyId("banco-digital"), "t", CaseType.SLOW_QUERY, Level.PLENO, 9L,
                new Narrative("t", "s", "b", List.of()), List.of(), List.of(p), new AnswerForm(List.of(), "solutionPath"),
                new ReferenceAnswer(""), new ScenarioSpec("slow-query", Map.of(
                        "service", "account-api", "endpoint", "GET /accounts/{id}/statement", "table", "ledger_entries",
                        "slowSql", "SELECT * FROM ledger_entries e WHERE e.account_id = $1 AND e.created_at >= $2 ORDER BY e.created_at DESC LIMIT 50",
                        "p99Before", "180", "p99After", "4200", "sinceAt", since.toString(), "dropIndex", "idx_ledger_account_created")),
                new PathRecommendation("composite-index", "r"), List.of(), Set.of(ConsoleTool.SQL, ConsoleTool.METRICS), Map.of(), now);
    }

    private final ObservabilityDatasetFactory factory = new ObservabilityDatasetFactory(List.of(new SlowQueryDatasetGenerator()));

    @Test
    void latencyOfAffectedServiceStepsUpAtIncidentStartWhileOthersStayFlat() {
        ObservabilityDataset ds = factory.generate(slowQueryCase(), bank, new SeededRandom(9L));

        MetricSeries p99 = ds.series("account-api", "latency_p99").orElseThrow();
        assertThat(p99.unit()).isEqualTo("ms");
        assertThat(ds.window().from()).isEqualTo(now.minusSeconds(24 * 3600));
        double before = p99.points().stream().filter(pt -> pt.at().isBefore(since.minusSeconds(600))).mapToDouble(MetricPoint::value).average().orElseThrow();
        double after = p99.points().stream().filter(pt -> pt.at().isAfter(since.plusSeconds(600))).mapToDouble(MetricPoint::value).average().orElseThrow();
        assertThat(before).isBetween(150.0, 220.0);
        assertThat(after).isBetween(3500.0, 5000.0);

        MetricSeries pixP99 = ds.series("pix-api", "latency_p99").orElseThrow();
        assertThat(pixP99.max()).isLessThan(600);
        assertThat(ds.series("core-db", "cpu_utilization")).isPresent();
        assertThat(ds.series("account-api", "db_pool_in_use")).isPresent();
        assertThat(ds.metrics().stream().map(MetricSeries::service).distinct()).contains("account-api", "pix-api", "receivables-api", "core-db");
    }

    @Test
    void databaseStateExposesTheSlowStatementAndDropsTheIndex() {
        ObservabilityDataset ds = factory.generate(slowQueryCase(), bank, new SeededRandom(9L));
        DbState db = ds.dbState();

        assertThat(db.statements().getFirst().query()).contains("FROM ledger_entries");
        assertThat(db.statements().getFirst().meanMs()).isBetween(3000.0, 5000.0);
        assertThat(db.statements()).hasSizeGreaterThan(5);
        assertThat(db.sessions()).anySatisfy(s -> { assertThat(s.query()).contains("ledger_entries"); assertThat(s.state()).isEqualTo("active"); });
        assertThat(db.tableStats()).anySatisfy(t -> { assertThat(t.table()).isEqualTo("ledger_entries"); assertThat(t.seqScan()).isGreaterThan(1000); });
        assertThat(db.effectiveSchema().table("ledger_entries").orElseThrow().indexes()).extracting("name").doesNotContain("idx_ledger_account_created");
    }

    @Test
    void isDeterministicForTheSameSeed() {
        ObservabilityDataset a = factory.generate(slowQueryCase(), bank, new SeededRandom(9L));
        ObservabilityDataset b = factory.generate(slowQueryCase(), bank, new SeededRandom(9L));
        assertThat(a.metrics()).isEqualTo(b.metrics());
        assertThat(a.dbState()).isEqualTo(b.dbState());
    }
}
