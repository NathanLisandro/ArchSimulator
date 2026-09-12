package dev.archsim.domain.cases;

import dev.archsim.domain.rubric.Rubric;

import java.util.List;

/** One valid way of solving the case, with its own rubric and the context in which it is the best choice. */
public record SolutionPath(String id, String name, String summary, List<String> pros, List<String> cons, String bestWhen, Rubric rubric) {}
