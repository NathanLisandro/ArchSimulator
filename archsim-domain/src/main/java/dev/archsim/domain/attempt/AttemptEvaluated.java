package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Instant;
import java.util.List;

public record AttemptEvaluated(AttemptId attemptId, UserId userId, CaseId caseId, CaseType caseType, List<String> competencies,
                               double score, long durationSeconds, Instant occurredAt) implements DomainEvent {}
