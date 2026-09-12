package dev.archsim.domain.progress;

import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;

import java.util.Map;

/**
 * Promotion needs three things at once: volume (cases solved), quality (average score) and breadth (distinct case
 * types practised). Seniority that comes from repeating the same exercise is not seniority.
 */
public final class PromotionPolicy {
    private record Requirement(int solved, double averageScore, int distinctTypes) {}

    private static final Map<Level, Requirement> NEXT = Map.of(
            Level.PLENO, new Requirement(10, 70, 4),
            Level.SENIOR, new Requirement(28, 78, 6),
            Level.STAFF, new Requirement(60, 85, 8));

    /** The highest rank the progress currently qualifies for; never below the rank already reached. */
    public Level rankFor(UserProgress progress, Level current) {
        Level best = current;
        for (Level candidate : new Level[]{Level.PLENO, Level.SENIOR, Level.STAFF}) {
            Requirement r = NEXT.get(candidate);
            if (progress.solvedCount() >= r.solved()
                    && progress.averageScore() >= r.averageScore()
                    && progress.distinctTypesPractised() >= r.distinctTypes()
                    && candidate.ordinal() > best.ordinal()) {
                best = candidate;
            }
        }
        return best;
    }

    /** What is still missing for the next rank, for the progress screen. */
    public String missingFor(UserProgress progress, Level current) {
        Level next = switch (current) {
            case JUNIOR -> Level.PLENO;
            case PLENO -> Level.SENIOR;
            case SENIOR -> Level.STAFF;
            case STAFF -> null;
        };
        if (next == null) return "Você chegou ao topo da trilha.";
        Requirement r = NEXT.get(next);
        StringBuilder sb = new StringBuilder("Para " + next.name().toLowerCase() + ": ");
        sb.append(progress.solvedCount()).append('/').append(r.solved()).append(" casos, ");
        sb.append(Math.round(progress.averageScore())).append('/').append(Math.round(r.averageScore())).append(" de média, ");
        sb.append(progress.distinctTypesPractised()).append('/').append(r.distinctTypes()).append(" tipos de caso.");
        return sb.toString();
    }

    public int caseTypeCount() { return CaseType.values().length; }
}
