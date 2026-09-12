package dev.archsim.domain.progress;

import dev.archsim.domain.attempt.AttemptEvaluated;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Instant;
import java.util.*;

/** Aggregate root: what the user has practised, how well, and the rank that follows from it. */
public class UserProgress {
    /** Below this average a case type counts as a weak area worth revisiting before broadening. */
    public static final double WEAK_THRESHOLD = 60;

    private final UserId userId;
    private Level rank;
    private long xp;
    private int solvedCount;
    private double totalScore;
    private final Map<CaseType, TypeStats> statsByType = new EnumMap<>(CaseType.class);
    private final Map<String, TypeStats> statsByCompetency = new LinkedHashMap<>();
    private final List<DomainEvent> events = new ArrayList<>();
    private Instant updatedAt;
    private final PromotionPolicy policy = new PromotionPolicy();

    private UserProgress(UserId userId, Level rank, Instant updatedAt) {
        this.userId = userId; this.rank = rank; this.updatedAt = updatedAt;
    }

    public static UserProgress start(UserId userId, Instant now) { return new UserProgress(userId, Level.JUNIOR, now); }

    public static UserProgress reconstitute(UserId userId, Level rank, long xp, int solvedCount, double totalScore,
                                            Map<CaseType, TypeStats> byType, Map<String, TypeStats> byCompetency, Instant updatedAt) {
        UserProgress p = new UserProgress(userId, rank, updatedAt);
        p.xp = xp; p.solvedCount = solvedCount; p.totalScore = totalScore;
        if (byType != null) p.statsByType.putAll(byType);
        if (byCompetency != null) p.statsByCompetency.putAll(byCompetency);
        return p;
    }

    /** Folds one evaluated attempt into the progress, promoting when the policy allows. */
    public void record(AttemptEvaluated attempt, Instant now) {
        solvedCount++;
        totalScore += attempt.score();
        xp += Math.round(attempt.score());
        statsByType.merge(attempt.caseType(), TypeStats.empty().with(attempt.score(), attempt.durationSeconds()),
                (existing, ignored) -> existing.with(attempt.score(), attempt.durationSeconds()));
        for (String competency : attempt.competencies()) {
            statsByCompetency.merge(competency, TypeStats.empty().with(attempt.score(), attempt.durationSeconds()),
                    (existing, ignored) -> existing.with(attempt.score(), attempt.durationSeconds()));
        }
        updatedAt = now;

        Level promoted = policy.rankFor(this, rank);
        if (promoted != rank) {
            events.add(new RankPromoted(userId, rank, promoted, now));
            rank = promoted;
        }
    }

    public double averageScore() { return solvedCount == 0 ? 0 : Math.round(totalScore / solvedCount * 100.0) / 100.0; }

    public int distinctTypesPractised() { return statsByType.size(); }

    public double competencyScore(String competency) {
        return Optional.ofNullable(statsByCompetency.get(competency)).map(TypeStats::averageScore).orElse(0.0);
    }

    /** Competencies with the lowest average first; only those actually practised. */
    public List<String> weakestCompetencies(int limit) {
        return statsByCompetency.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, TypeStats> e) -> e.getValue().averageScore())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit).map(Map.Entry::getKey).toList();
    }

    public List<String> strongestCompetencies(int limit) {
        return statsByCompetency.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, TypeStats> e) -> e.getValue().averageScore()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit).map(Map.Entry::getKey).toList();
    }

    /**
     * A weak area beats breadth: while some practised type is below {@value #WEAK_THRESHOLD}, train that one.
     * Once nothing is weak, suggest a type never practised (breadth is a promotion requirement), then the lowest average.
     */
    public CaseType suggestedNextType() {
        Optional<CaseType> weakest = statsByType.entrySet().stream()
                .filter(e -> e.getValue().averageScore() < WEAK_THRESHOLD)
                .min(Comparator.comparingDouble(e -> e.getValue().averageScore()))
                .map(Map.Entry::getKey);
        if (weakest.isPresent()) return weakest.get();
        for (CaseType type : CaseType.values()) if (!statsByType.containsKey(type)) return type;
        return statsByType.entrySet().stream()
                .min(Comparator.comparingDouble(e -> e.getValue().averageScore()))
                .map(Map.Entry::getKey).orElse(CaseType.SLOW_QUERY);
    }

    public String missingForNextRank() { return policy.missingFor(this, rank); }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    public UserId userId() { return userId; }
    public Level rank() { return rank; }
    public long xp() { return xp; }
    public int solvedCount() { return solvedCount; }
    public double totalScore() { return totalScore; }
    public Map<CaseType, TypeStats> statsByType() { return Map.copyOf(statsByType); }
    public Map<String, TypeStats> statsByCompetency() { return Map.copyOf(statsByCompetency); }
    public Instant updatedAt() { return updatedAt; }
}
