package dev.archsim.domain.attempt;

import java.util.Map;

public record GraphEdge(String from, String to, String label, boolean async, Map<String, String> props) {}
