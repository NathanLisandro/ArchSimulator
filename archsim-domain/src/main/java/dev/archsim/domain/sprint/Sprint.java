package dev.archsim.domain.sprint;

import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Aggregate root: N cases in a row against the clock. Finishes on the last case or when time runs out. */
public class Sprint {
    private final SprintId id;
    private final UserId userId;
    private final int totalCases;
    private final Duration timeLimit;
    private final Level level;
    private final CaseType caseType;
    private final Instant startedAt;
    private SprintStatus status;
    private Instant finishedAt;
    private final List<SprintResult> results = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();

    private Sprint(SprintId id, UserId userId, int totalCases, Duration timeLimit, Level level, CaseType caseType,
                   Instant startedAt, SprintStatus status) {
        this.id = id; this.userId = userId; this.totalCases = totalCases; this.timeLimit = timeLimit;
        this.level = level; this.caseType = caseType; this.startedAt = startedAt; this.status = status;
    }

    public static Sprint start(SprintId id, UserId userId, int totalCases, Duration timeLimit, Level level, CaseType caseType, Instant now) {
        if (totalCases <= 0) throw new IllegalArgumentException("Um sprint precisa de pelo menos um caso");
        if (timeLimit == null || timeLimit.isZero() || timeLimit.isNegative()) throw new IllegalArgumentException("O sprint precisa de um limite de tempo positivo");
        return new Sprint(id, userId, totalCases, timeLimit, level, caseType, now, SprintStatus.RUNNING);
    }

    public static Sprint reconstitute(SprintId id, UserId userId, int totalCases, Duration timeLimit, Level level, CaseType caseType,
                                      Instant startedAt, SprintStatus status, Instant finishedAt, List<SprintResult> results) {
        Sprint s = new Sprint(id, userId, totalCases, timeLimit, level, caseType, startedAt, status);
        s.finishedAt = finishedAt;
        if (results != null) s.results.addAll(results);
        return s;
    }

    public void recordResult(AttemptId attemptId, CaseType type, double score, Instant now) {
        if (status != SprintStatus.RUNNING) throw new IllegalStateException("Este sprint já terminou");
        results.add(new SprintResult(attemptId, type, score, now, Duration.between(startedAt, now).getSeconds()));
        if (results.size() >= totalCases) finish(SprintStatus.FINISHED, now);
    }

    /** Called on every read of a running sprint; closes it when the clock has run out. */
    public void expireIfDue(Instant now) {
        if (status == SprintStatus.RUNNING && expired(now)) finish(SprintStatus.EXPIRED, now);
    }

    private void finish(SprintStatus finalStatus, Instant now) {
        status = finalStatus;
        finishedAt = now;
        events.add(new SprintFinished(id, userId, finalStatus, results.size(), totalCases, averageScore(), now));
    }

    public boolean expired(Instant now) { return now.isAfter(deadline()); }
    public Instant deadline() { return startedAt.plus(timeLimit); }

    public Duration remaining(Instant now) {
        if (status != SprintStatus.RUNNING) return Duration.ZERO;
        Duration left = Duration.between(now, deadline());
        return left.isNegative() ? Duration.ZERO : left;
    }

    public double averageScore() {
        return results.isEmpty() ? 0 : Math.round(results.stream().mapToDouble(SprintResult::score).average().orElse(0) * 10.0) / 10.0;
    }

    public Optional<SprintResult> bestResult() { return results.stream().max(Comparator.comparingDouble(SprintResult::score)); }
    public Optional<SprintResult> worstResult() { return results.stream().min(Comparator.comparingDouble(SprintResult::score)); }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    public SprintId id() { return id; }
    public UserId userId() { return userId; }
    public int totalCases() { return totalCases; }
    public int completedCases() { return results.size(); }
    public int remainingCases() { return Math.max(0, totalCases - results.size()); }
    public Duration timeLimit() { return timeLimit; }
    public Level level() { return level; }
    public Optional<CaseType> caseType() { return Optional.ofNullable(caseType); }
    public Instant startedAt() { return startedAt; }
    public Optional<Instant> finishedAt() { return Optional.ofNullable(finishedAt); }
    public SprintStatus status() { return status; }
    public List<SprintResult> results() { return List.copyOf(results); }
}
