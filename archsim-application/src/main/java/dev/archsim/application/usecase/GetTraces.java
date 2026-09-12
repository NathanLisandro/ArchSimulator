package dev.archsim.application.usecase;

import dev.archsim.application.dto.TracesView;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.observability.Trace;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.util.Comparator;
import java.util.List;

/** Trace console: the slowest traces first, or one trace in full by id. */
public class GetTraces {
    public record Command(UserId userId, String attemptId, String traceId, String service) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final ObservabilityDatasetProvider datasets;
    private final Clock clock;

    public GetTraces(AttemptRepository attempts, CaseRepository cases, ObservabilityDatasetProvider datasets, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.datasets = datasets; this.clock = clock;
    }

    public TracesView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        List<Trace> all = datasets.datasetFor(loaded.theCase()).traces();
        List<Trace> selected = all.stream()
                .filter(t -> cmd.traceId() == null || t.traceId().equals(cmd.traceId()))
                .filter(t -> cmd.service() == null || t.rootService().equals(cmd.service())
                        || t.spans().stream().anyMatch(s -> s.service().equals(cmd.service())))
                .sorted(Comparator.comparingLong(Trace::durationMs).reversed())
                .limit(cmd.traceId() == null ? 20 : 1)
                .toList();
        loaded.attempt().recordConsoleAction(new ConsoleAction(ConsoleActionKind.TRACE_VIEW,
                cmd.traceId() != null ? cmd.traceId() : (cmd.service() == null ? "traces" : cmd.service()), "traces", clock.now()));
        attempts.save(loaded.attempt());
        return new TracesView(selected, all.size());
    }
}
