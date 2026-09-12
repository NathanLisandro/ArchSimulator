package dev.archsim.domain.attempt;

import dev.archsim.domain.console.ConsoleActionKind;

import java.time.Instant;

/** One thing the user did in the simulated console (a query, a panel opened, a mitigation applied). */
public record ConsoleAction(ConsoleActionKind kind, String target, String detail, Instant at) {}
