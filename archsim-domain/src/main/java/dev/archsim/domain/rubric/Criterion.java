package dev.archsim.domain.rubric;

import java.util.List;

public record Criterion(String id, String name, int weight, List<Evidence> evidence, String feedbackWhenMissing, String feedbackWhenPresent) {
    public Criterion { if (weight <= 0) throw new IllegalArgumentException("weight must be positive: " + id); }

    /** Fraction of evidence satisfied, 0..1. A criterion without evidence is never satisfied automatically. */
    public double fractionMatched(AnswerContext ctx) {
        if (evidence.isEmpty()) return 0;
        long hits = evidence.stream().filter(e -> e.matches(ctx)).count();
        return (double) hits / evidence.size();
    }
}
