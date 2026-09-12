package dev.archsim.domain.progress;

/** Rolling statistics for one case type. */
public record TypeStats(int attempts, double totalScore, double bestScore, long totalSeconds) {
    public static TypeStats empty() { return new TypeStats(0, 0, 0, 0); }

    public TypeStats with(double score, long seconds) {
        return new TypeStats(attempts + 1, totalScore + score, Math.max(bestScore, score), totalSeconds + seconds);
    }

    public double averageScore() { return attempts == 0 ? 0 : round(totalScore / attempts); }
    public long averageSeconds() { return attempts == 0 ? 0 : totalSeconds / attempts; }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
