package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.Verdict;

/** The card shown right after a checkpoint choice. requiresConfirmation=true lets the user revise or proceed anyway. */
public record DirectionalFeedback(String checkpointId, String optionId, Verdict verdict, String message, boolean requiresConfirmation) {}
