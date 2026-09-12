package dev.archsim.application.dto;

import dev.archsim.domain.attempt.SolutionGraph;

import java.util.Map;

public record AnswerInput(Map<String, String> fields, String markdown, String mermaid, SolutionGraph graph) {}
