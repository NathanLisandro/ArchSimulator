package dev.archsim.application.usecase;

import dev.archsim.application.dto.MetricsView;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.observability.MetricSeries;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.time.Instant;
import java.util.List;

/** Metrics explorer: filters series by service/metric names and window. */
public class QueryMetrics {
    public record Command(UserId userId, String attemptId, String service, List<String> metrics, Instant from, Instant to) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final ObservabilityDatasetProvider datasets;
    private final Clock clock;

    public QueryMetrics(AttemptRepository attempts, CaseRepository cases, ObservabilityDatasetProvider datasets, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.datasets = datasets; this.clock = clock;
    }

    public MetricsView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        ObservabilityDataset ds = datasets.datasetFor(loaded.theCase(), loaded.attempt().resolvedAt().orElse(null));
        Instant from = cmd.from() == null ? ds.window().from() : cmd.from();
        Instant to = cmd.to() == null ? ds.window().to() : cmd.to();
        List<MetricSeries> series = ds.metrics().stream()
                .filter(s -> cmd.service() == null || s.service().equals(cmd.service()))
                .filter(s -> cmd.metrics() == null || cmd.metrics().isEmpty() || cmd.metrics().contains(s.metric()))
                .map(s -> new MetricSeries(s.service(), s.metric(), s.unit(), s.points().stream().filter(p -> !p.at().isBefore(from) && !p.at().isAfter(to)).toList()))
                .toList();
        String target = (cmd.service() == null ? "*" : cmd.service()) + ":" + (cmd.metrics() == null || cmd.metrics().isEmpty() ? "*" : String.join("|", cmd.metrics()));
        loaded.attempt().recordConsoleAction(new ConsoleAction(ConsoleActionKind.METRICS_VIEW, target, from + ".." + to, clock.now()));
        attempts.save(loaded.attempt());
        return new MetricsView(from, to, ds.services(), series);
    }
}
