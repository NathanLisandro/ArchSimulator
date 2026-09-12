package dev.archsim.domain.sprint;

import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Instant;

public record SprintFinished(SprintId sprintId, UserId userId, SprintStatus status, int completedCases, int totalCases,
                             double averageScore, Instant occurredAt) implements DomainEvent {}
