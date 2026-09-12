package dev.archsim.application.dto;

import dev.archsim.domain.sprint.Sprint;
import dev.archsim.domain.sprint.SprintResult;

import java.time.Instant;
import java.util.List;

public record SprintView(String id, String status, int totalCases, int completedCases, int remainingCases,
                         long remainingSeconds, long timeLimitSeconds, String level, String caseType,
                         double averageScore, Instant startedAt, Instant deadline, List<SprintResult> results) {

    public static SprintView from(Sprint s, Instant now) {
        return new SprintView(s.id().value(), s.status().name(), s.totalCases(), s.completedCases(), s.remainingCases(),
                s.remaining(now).getSeconds(), s.timeLimit().getSeconds(), s.level().name(),
                s.caseType().map(Enum::name).orElse(null), s.averageScore(), s.startedAt(), s.deadline(), s.results());
    }
}
