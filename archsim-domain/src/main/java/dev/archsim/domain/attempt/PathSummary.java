package dev.archsim.domain.attempt;

import java.util.List;

public record PathSummary(String id, String name, String bestWhen, List<String> pros, List<String> cons) {}
