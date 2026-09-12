package dev.archsim.domain.attempt;

import java.util.Map;

public record GraphNode(String id, String type, String label, Map<String, String> props) {}
