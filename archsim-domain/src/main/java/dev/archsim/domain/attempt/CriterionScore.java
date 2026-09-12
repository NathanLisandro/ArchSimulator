package dev.archsim.domain.attempt;

public record CriterionScore(String id, String name, int weight, double score, int matched, int total, String feedback) {}
