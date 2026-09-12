package dev.archsim.domain.sprint;

import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.cases.CaseType;

import java.time.Instant;

public record SprintResult(AttemptId attemptId, CaseType caseType, double score, Instant submittedAt, long elapsedSeconds) {}
