package dev.archsim.application.usecase;

import dev.archsim.application.dto.*;
import dev.archsim.application.fakes.InMemory;
import dev.archsim.application.fakes.TestTemplates;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleUseCasesTest {
    private final InMemory.Companies companies = new InMemory.Companies(TestTemplates.bank());
    private final InMemory.Templates templates = new InMemory.Templates(TestTemplates.incident(Level.SENIOR));
    private final InMemory.Cases cases = new InMemory.Cases();
    private final InMemory.Attempts attempts = new InMemory.Attempts();
    private final InMemory.FixedClock clock = new InMemory.FixedClock(Instant.parse("2026-09-12T18:00:00Z"));
    private final InMemory.Datasets datasets = new InMemory.Datasets(companies);
    private final UserId user = new UserId("u1");

    private final StartCase startCase = new StartCase(companies, templates, cases, attempts, new dev.archsim.domain.template.CaseGenerator(), new InMemory.Seeds(), clock);
    private final SearchLogs searchLogs = new SearchLogs(attempts, cases, datasets, clock);
    private final GetTraces getTraces = new GetTraces(attempts, cases, datasets, clock);
    private final QueryMetrics queryMetrics = new QueryMetrics(attempts, cases, datasets, clock);
    private final GetTimeline getTimeline = new GetTimeline(attempts, cases, companies, datasets, clock);
    private final ApplyMitigation applyMitigation = new ApplyMitigation(attempts, cases, clock);
    private final PostStatusUpdate postStatusUpdate = new PostStatusUpdate(attempts, cases, clock);
    private final GetAttempt getAttempt = new GetAttempt(attempts, cases, clock);

    private String startAttempt() {
        return startCase.execute(new StartCase.Command(user, "banco-digital", "INCIDENT", Level.SENIOR)).id();
    }

    @Test
    void searchesLogsWithTheMiniLanguageAndRecordsTheTrail() {
        String id = startAttempt();
        LogSearchView events = searchLogs.execute(new SearchLogs.Command(user, id, "service=account-api level=ERROR", null, null));
        assertThat(events.kind()).isEqualTo("EVENTS");
        assertThat(events.events()).isNotEmpty();
        assertThat(events.totalMatched()).isGreaterThan(events.events().size() - 1L);

        LogSearchView stats = searchLogs.execute(new SearchLogs.Command(user, id, "| stats count by service | sort -count", null, null));
        assertThat(stats.kind()).isEqualTo("TABLE");
        assertThat(stats.columns()).containsExactly("service", "count");
        assertThat(stats.rows()).isNotEmpty();

        LogSearchView bad = searchLogs.execute(new SearchLogs.Command(user, id, "| frobnicate", null, null));
        assertThat(bad.error()).contains("frobnicate");
        assertThat(bad.events()).isEmpty();

        assertThat(getAttempt.execute(user, id).trail()).extracting(a -> a.kind())
                .containsExactly(ConsoleActionKind.LOG_SEARCH, ConsoleActionKind.LOG_SEARCH, ConsoleActionKind.LOG_SEARCH);
    }

    @Test
    void filtersLogsByWindowAndListsTraces() {
        String id = startAttempt();
        Instant to = clock.now;
        Instant from = to.minusSeconds(600);
        LogSearchView windowed = searchLogs.execute(new SearchLogs.Command(user, id, "", from, to));
        assertThat(windowed.events()).allSatisfy(e -> assertThat(e.timestamp()).isBetween(from, to));

        TracesView traces = getTraces.execute(new GetTraces.Command(user, id, null, null));
        assertThat(traces.traces()).isNotEmpty();
        assertThat(traces.traces().getFirst().spans()).isNotEmpty();

        String traceId = traces.traces().getFirst().traceId();
        TracesView one = getTraces.execute(new GetTraces.Command(user, id, traceId, null));
        assertThat(one.traces()).hasSize(1);
        assertThat(getAttempt.execute(user, id).trail()).extracting(a -> a.kind()).contains(ConsoleActionKind.TRACE_VIEW);
    }

    @Test
    void timelineJoinsDeploysFlagsAlertsAndInfra() {
        String id = startAttempt();
        TimelineView timeline = getTimeline.execute(new GetTimeline.Command(user, id));
        assertThat(timeline.deploys()).isNotEmpty();
        assertThat(timeline.alerts()).isNotEmpty();
        assertThat(timeline.instances()).isNotEmpty();
        assertThat(timeline.infraResources()).isNotEmpty();
        assertThat(timeline.deploys()).isSortedAccordingTo((a, b) -> a.at().compareTo(b.at()));
        assertThat(getAttempt.execute(user, id).trail()).extracting(a -> a.kind()).contains(ConsoleActionKind.DEPLOYS_VIEW);
    }

    @Test
    void appliesMitigationAndPostsStatusUpdates() {
        String id = startAttempt();
        searchLogs.execute(new SearchLogs.Command(user, id, "level=ERROR", null, null));
        clock.now = clock.now.plusSeconds(420);

        MitigationView outcome = applyMitigation.execute(new ApplyMitigation.Command(user, id, "rollback-deploy"));
        assertThat(outcome.effect()).isEqualTo("RESOLVES");
        assertThat(outcome.incidentResolved()).isTrue();
        assertThat(outcome.message()).isNotBlank();

        postStatusUpdate.execute(new PostStatusUpdate.Command(user, id, "Impacto mapeado, rollback em andamento."));
        AttemptView view = getAttempt.execute(user, id);
        assertThat(view.statusUpdates()).hasSize(1);
        assertThat(view.appliedMitigations()).hasSize(1);
        assertThat(view.pendingStakeholderMessages()).isNotNull();

        assertThatThrownBy(() -> applyMitigation.execute(new ApplyMitigation.Command(user, id, "nope")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metricsRecoverAfterTheMitigationIsApplied() {
        String id = startAttempt();
        double degraded = latestLatency(id);
        double peakWhileBroken = peakLatency(id);
        assertThat(degraded).as("o incidente começa degradado").isGreaterThan(2000);

        searchLogs.execute(new SearchLogs.Command(user, id, "level=ERROR", null, null));
        applyMitigation.execute(new ApplyMitigation.Command(user, id, "rollback-deploy"));

        assertThat(latestLatency(id)).as("depois da mitigação o p99 volta ao normal").isLessThan(degraded / 3);
        assertThat(peakLatency(id)).as("o passado não muda: o pico do incidente continua no gráfico")
                .isCloseTo(peakWhileBroken, org.assertj.core.data.Percentage.withPercentage(5));
    }

    private double latestLatency(String attemptId) {
        return series(attemptId).points().getLast().value();
    }

    private double peakLatency(String attemptId) {
        return series(attemptId).points().stream().mapToDouble(p -> p.value()).max().orElseThrow();
    }

    private dev.archsim.domain.observability.MetricSeries series(String attemptId) {
        MetricsView view = queryMetrics.execute(new QueryMetrics.Command(user, attemptId, "account-api", List.of("latency_p99"), null, null));
        return view.series().getFirst();
    }

    @Test
    void stakeholderMessagesShowUpAsTimePasses() {
        String id = startAttempt();
        assertThat(getAttempt.execute(user, id).stakeholderMessages()).isEmpty();
        clock.now = clock.now.plusSeconds(400);
        List<CaseView.StakeholderView> arrived = getAttempt.execute(user, id).stakeholderMessages();
        assertThat(arrived).isNotEmpty();
        assertThat(arrived.getFirst().text()).isNotBlank();
    }
}
