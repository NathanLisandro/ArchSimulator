package dev.archsim.domain.progress;

import dev.archsim.domain.cases.Level;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Instant;

public record RankPromoted(UserId userId, Level from, Level to, Instant occurredAt) implements DomainEvent {}
