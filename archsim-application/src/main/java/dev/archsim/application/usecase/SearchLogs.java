package dev.archsim.application.usecase;

import dev.archsim.application.dto.LogSearchView;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.logquery.LogQueryEngine;
import dev.archsim.domain.logquery.LogQueryException;
import dev.archsim.domain.observability.LogEntry;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.time.Instant;
import java.util.List;

/** Log console: runs the mini-language over the case dataset, optionally restricted to a time window. */
public class SearchLogs {
    public record Command(UserId userId, String attemptId, String query, Instant from, Instant to) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final ObservabilityDatasetProvider datasets;
    private final Clock clock;
    private final LogQueryEngine engine = new LogQueryEngine();

    public SearchLogs(AttemptRepository attempts, CaseRepository cases, ObservabilityDatasetProvider datasets, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.datasets = datasets; this.clock = clock;
    }

    public LogSearchView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        List<LogEntry> logs = datasets.datasetFor(loaded.theCase(), loaded.attempt().resolvedAt().orElse(null)).logs().stream()
                .filter(l -> cmd.from() == null || !l.timestamp().isBefore(cmd.from()))
                .filter(l -> cmd.to() == null || !l.timestamp().isAfter(cmd.to()))
                .toList();
        String query = cmd.query() == null ? "" : cmd.query();
        LogSearchView view;
        try {
            view = LogSearchView.from(engine.run(query, logs));
        } catch (LogQueryException e) {
            view = LogSearchView.error(e.getMessage());
        }
        loaded.attempt().recordConsoleAction(new ConsoleAction(ConsoleActionKind.LOG_SEARCH, queryTarget(query), query, clock.now()));
        attempts.save(loaded.attempt());
        return view;
    }

    /** Best-effort "what was this search about", used by investigation evidence. */
    private static String queryTarget(String query) {
        var m = java.util.regex.Pattern.compile("(?i)service\\s*=\\s*([A-Za-z0-9_.*-]+)").matcher(query);
        return m.find() ? m.group(1) : "logs";
    }
}
