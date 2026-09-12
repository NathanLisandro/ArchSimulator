package dev.archsim.domain.attempt;

import java.util.List;

public record PathComparison(String followedPathId, String recommendedPathId, String message, List<PathSummary> alternatives) {}
