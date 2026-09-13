package dev.archsim.domain.observability;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.fixtures.Fixtures;
import dev.archsim.domain.rubric.Rubric;
import dev.archsim.domain.shared.SeededRandom;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentDatasetGeneratorTest {
    private static final Instant NOW = Instant.parse("2026-09-12T18:00:00Z");
    private static final Instant DEPLOY = Instant.parse("2026-09-12T17:32:00Z");
    private static final Instant ONSET = Instant.parse("2026-09-12T17:35:00Z");

    private final Company bank = Fixtures.bank();
    private final ObservabilityDatasetFactory factory = new ObservabilityDatasetFactory(List.of(new IncidentDatasetGenerator()));

    private Case incident(String rootCause, Map<String, String> extra) {
        Map<String, String> params = new HashMap<>(Map.of(
                "rootCause", rootCause,
                "service", "account-api",
                "datastore", "core-db",
                "deployAt", DEPLOY.toString(),
                "onsetAt", ONSET.toString(),
                "p99Before", "180",
                "p99After", "4200",
                "deployService", "account-api",
                "deployVersion", "2026.37.4"));
        params.putAll(extra);
        SolutionPath p = new SolutionPath("rollback", "Rollback", "s", List.of(), List.of(), "w", new Rubric(List.of()));
        return new Case(CaseId.of("c-inc"), new CompanyId("banco-digital"), "t", CaseType.INCIDENT, Level.SENIOR, 77L,
                new Narrative("t", "s", "b", List.of()), List.of(), List.of(p), new AnswerForm(List.of(), "solutionPath"),
                new ReferenceAnswer(""), new ScenarioSpec("incident", params), new PathRecommendation("rollback", "r"),
                List.of("incident"), Set.of(ConsoleTool.LOGS, ConsoleTool.METRICS, ConsoleTool.TRACES, ConsoleTool.DEPLOYS), Map.of(), NOW);
    }

    @Test
    void producesCorrelatedLogsMetricsTracesAndDeploysForAnNPlusOne() {
        ObservabilityDataset ds = factory.generate(incident("N_PLUS_ONE", Map.of()), bank, new SeededRandom(77L));

        assertThat(ds.window().from()).isEqualTo(NOW.minus(Duration.ofHours(6)));
        assertThat(ds.logs()).hasSizeGreaterThan(3000);
        assertThat(ds.logs()).allSatisfy(l -> assertThat(ds.window().contains(l.timestamp())).isTrue());
        assertThat(ds.logs().stream().map(LogEntry::service).distinct()).contains("account-api", "pix-api", "receivables-api");

        long errorsBefore = ds.logs().stream().filter(l -> l.level().equals("ERROR") && l.timestamp().isBefore(ONSET)).count();
        long errorsAfter = ds.logs().stream().filter(l -> l.level().equals("ERROR") && l.timestamp().isAfter(ONSET)).count();
        assertThat(errorsAfter).isGreaterThan(errorsBefore * 5);

        MetricSeries p99 = ds.series("account-api", "latency_p99").orElseThrow();
        double before = avgBetween(p99, ds.window().from(), ONSET.minusSeconds(600));
        double after = avgBetween(p99, ONSET.plusSeconds(600), ds.window().to());
        assertThat(before).isBetween(120.0, 260.0);
        assertThat(after).isGreaterThan(3000);
        assertThat(ds.series("pix-api", "latency_p99").orElseThrow().max()).isLessThan(900);

        assertThat(ds.deploys()).isNotEmpty();
        assertThat(ds.deploys()).anySatisfy(d -> {
            assertThat(d.service()).isEqualTo("account-api");
            assertThat(d.at()).isEqualTo(DEPLOY);
        });
        assertThat(ds.deploys()).as("red herring: outro deploy sem relação").hasSizeGreaterThan(1);
        assertThat(ds.alerts()).isNotEmpty();
        assertThat(ds.instances()).isNotEmpty();

        assertThat(ds.traces()).isNotEmpty();
        Trace slow = ds.traces().stream().max((a, b) -> Long.compare(a.durationMs(), b.durationMs())).orElseThrow();
        assertThat(slow.spans()).hasSizeGreaterThan(20);
        assertThat(slow.spans().stream().filter(s -> s.operation().startsWith("SELECT")).count()).isGreaterThan(15);
        assertThat(ds.logs().stream().map(LogEntry::traceId)).contains(slow.traceId());
    }

    @Test
    void poolExhaustionShowsPoolSaturationAndTimeoutLogs() {
        ObservabilityDataset ds = factory.generate(incident("POOL_EXHAUSTION", Map.of()), bank, new SeededRandom(5L));
        assertThat(ds.series("account-api", "db_pool_in_use").orElseThrow().max()).isGreaterThanOrEqualTo(19);
        assertThat(ds.logs()).anySatisfy(l -> assertThat(l.message()).containsIgnoringCase("connection"));
        assertThat(ds.dbState().sessions()).isNotEmpty();
    }

    @Test
    void kafkaHotPartitionShowsConsumerLagOnly() {
        ObservabilityDataset ds = factory.generate(incident("KAFKA_HOT_PARTITION", Map.of("broker", "kafka", "topic", "ledger.entries")), bank, new SeededRandom(9L));
        MetricSeries lag = ds.series("kafka", "consumer_lag").orElseThrow();
        assertThat(lag.last()).isGreaterThan(lag.points().getFirst().value() * 10);
        assertThat(ds.logs()).anySatisfy(l -> assertThat(l.message().toLowerCase()).contains("partition"));
    }

    @Test
    void memoryLeakShowsSawtoothLosingItsFloorAndRestarts() {
        ObservabilityDataset ds = factory.generate(incident("MEMORY_LEAK", Map.of()), bank, new SeededRandom(3L));
        MetricSeries mem = ds.series("account-api", "memory_used").orElseThrow();
        double firstQuarter = avgBetween(mem, ds.window().from(), ds.window().from().plus(Duration.ofHours(1)));
        double lastQuarter = avgBetween(mem, ds.window().to().minus(Duration.ofHours(1)), ds.window().to());
        assertThat(lastQuarter).isGreaterThan(firstQuarter + 10);
        assertThat(ds.instances()).anySatisfy(i -> assertThat(i.restarts()).isGreaterThan(0));
    }

    @Test
    void dlqGrowsSilentlyWithoutLatencyImpact() {
        ObservabilityDataset ds = factory.generate(incident("DLQ_GROWING", Map.of("queue", "notifications-sms", "service", "notification-service")), bank, new SeededRandom(4L));
        assertThat(ds.series("notification-service", "dlq_messages").orElseThrow().last()).isGreaterThan(100);
        assertThat(ds.series("account-api", "latency_p99").orElseThrow().max()).isLessThan(900);
    }

    @Test
    void everyRootCauseGeneratesADatasetDeterministically() {
        for (IncidentRootCause cause : IncidentRootCause.values()) {
            Map<String, String> extra = Map.of("broker", "kafka", "topic", "ledger.entries", "queue", "notifications-sms",
                    "cache", "redis-cache", "partner", "SPI (BCB)", "table", "ledger_entries", "index", "idx_ledger_account_created");
            Case c = incident(cause.name(), extra);
            ObservabilityDataset a = factory.generate(c, bank, new SeededRandom(21L));
            ObservabilityDataset b = factory.generate(c, bank, new SeededRandom(21L));
            assertThat(a.logs()).as(cause.name()).isEqualTo(b.logs()).isNotEmpty();
            assertThat(a.metrics()).as(cause.name()).isEqualTo(b.metrics());
            assertThat(a.deploys()).as(cause.name()).isEqualTo(b.deploys());
            assertThat(a.alerts()).as(cause.name()).isNotEmpty();
        }
        assertThat(IncidentRootCause.values()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void appliedMitigationBringsTheMetricsBackToBaseline() {
        Case c = incident("N_PLUS_ONE", Map.of());
        Instant recovered = ONSET.plus(Duration.ofMinutes(12));
        ObservabilityDataset before = factory.generate(c, bank, new SeededRandom(77L));
        ObservabilityDataset after = factory.generate(c, bank, new SeededRandom(77L), recovered);

        MetricSeries stillBroken = before.series("account-api", "latency_p99").orElseThrow();
        MetricSeries healed = after.series("account-api", "latency_p99").orElseThrow();

        // até a mitigação, os dois datasets são idênticos: o passado não muda
        assertThat(avgBetween(healed, ds -> true, ONSET.minus(Duration.ofHours(1)), ONSET))
                .isCloseTo(avgBetween(stillBroken, ds -> true, ONSET.minus(Duration.ofHours(1)), ONSET), org.assertj.core.data.Offset.offset(1.0));

        // no pico do incidente, os dois estão degradados
        assertThat(avgBetween(healed, ds -> true, ONSET.plus(Duration.ofMinutes(9)), recovered)).isGreaterThan(3000);

        // depois da mitigação, só o dataset com recuperação volta ao normal
        double afterRecovery = avgBetween(healed, ds -> true, recovered.plus(Duration.ofMinutes(6)), NOW);
        double withoutRecovery = avgBetween(stillBroken, ds -> true, recovered.plus(Duration.ofMinutes(6)), NOW);
        assertThat(afterRecovery).isLessThan(600);
        assertThat(withoutRecovery).isGreaterThan(3000);
    }

    @Test
    void errorLogsStopAfterTheIncidentIsMitigated() {
        Case c = incident("POOL_EXHAUSTION", Map.of());
        Instant recovered = ONSET.plus(Duration.ofMinutes(10));
        ObservabilityDataset after = factory.generate(c, bank, new SeededRandom(5L), recovered);

        long errorsWhileBroken = after.logs().stream()
                .filter(l -> l.level().equals("ERROR") && l.service().equals("account-api"))
                .filter(l -> l.timestamp().isAfter(ONSET) && l.timestamp().isBefore(recovered))
                .count();
        long errorsAfterFix = after.logs().stream()
                .filter(l -> l.level().equals("ERROR") && l.service().equals("account-api"))
                .filter(l -> l.timestamp().isAfter(recovered.plus(Duration.ofMinutes(3))))
                .count();

        assertThat(errorsWhileBroken).isGreaterThan(20);
        assertThat(errorsAfterFix).isLessThan(errorsWhileBroken / 4);
    }

    @Test
    void unknownRootCauseFailsLoudly() {
        assertThatThrownBy(() -> factory.generate(incident("NOT_A_CAUSE", Map.of()), bank, new SeededRandom(1L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NOT_A_CAUSE");
    }

    private static double avgBetween(MetricSeries s, Instant from, Instant to) {
        return s.points().stream().filter(p -> !p.at().isBefore(from) && !p.at().isAfter(to)).mapToDouble(MetricPoint::value).average().orElseThrow();
    }

    private static double avgBetween(MetricSeries s, java.util.function.Predicate<MetricSeries> ignored, Instant from, Instant to) {
        return avgBetween(s, from, to);
    }
}
