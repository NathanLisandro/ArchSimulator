package dev.archsim.application.dto;

import dev.archsim.domain.progress.TypeStats;
import dev.archsim.domain.progress.UserProgress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProgressView(String rank, long xp, int solvedCount, double averageScore, Map<String, TypeStats> byType,
                           Map<String, TypeStats> byCompetency, List<String> weakest, List<String> strongest,
                           String missingForNextRank, String suggestedNextType, List<AttemptSummaryView> recentAttempts) {

    public static ProgressView from(UserProgress p, List<AttemptSummaryView> recent) {
        Map<String, TypeStats> byType = new LinkedHashMap<>();
        p.statsByType().forEach((type, stats) -> byType.put(type.name(), stats));
        return new ProgressView(p.rank().name(), p.xp(), p.solvedCount(), p.averageScore(), byType,
                p.statsByCompetency(), p.weakestCompetencies(3), p.strongestCompetencies(3),
                p.missingForNextRank(), p.suggestedNextType().name(), recent);
    }
}
