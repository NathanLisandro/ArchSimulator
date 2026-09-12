package dev.archsim.application.dto;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.cases.Case;

import java.time.Instant;

public record AttemptSummaryView(String id, String caseId, String caseType, String caseTitle, String companyId, String level,
                                 String status, Double score, String followedPath, Instant startedAt, Instant submittedAt,
                                 long durationSeconds, String sprintId) {

    public static AttemptSummaryView from(Attempt a, Case c) {
        return new AttemptSummaryView(a.id().value(), c.id().value(), c.type().name(), c.narrative().title(),
                c.companyId().value(), c.level().name(), a.status().name(),
                a.evaluation().map(e -> e.totalScore()).orElse(null),
                a.evaluation().map(e -> c.solutionPath(e.followedPathId()).map(p -> p.name()).orElse(e.followedPathId())).orElse(null),
                a.startedAt(), a.submittedAt(), a.durationSeconds(), a.sprintId().orElse(null));
    }
}
