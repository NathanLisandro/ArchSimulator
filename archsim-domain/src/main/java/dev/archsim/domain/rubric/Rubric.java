package dev.archsim.domain.rubric;

import java.util.List;

public record Rubric(List<Criterion> criteria) {
    public int totalWeight() { return criteria.stream().mapToInt(Criterion::weight).sum(); }
}
