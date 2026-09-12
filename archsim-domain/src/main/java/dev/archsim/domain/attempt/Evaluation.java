package dev.archsim.domain.attempt;

import java.time.Instant;
import java.util.List;

public record Evaluation(double totalScore, String followedPathId, List<CriterionScore> criteria, List<String> strengths, List<String> gaps,
                         PathComparison comparison, String referenceMarkdown, List<String> investigationNotes, Instant evaluatedAt) {}
