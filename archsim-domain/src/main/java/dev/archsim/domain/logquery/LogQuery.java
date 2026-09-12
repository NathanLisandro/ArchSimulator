package dev.archsim.domain.logquery;

import java.util.List;

public record LogQuery(LogFilter filter, List<Stage> pipeline) {}
