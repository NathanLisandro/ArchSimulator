package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.Verdict;

import java.time.Instant;

/**
 * The current answer to a checkpoint. confirmed=false means the user saw a warning and has not yet decided;
 * proceededDespiteWarning=true means they chose to keep a RISKY/BAD option after the warning.
 */
public record CheckpointAnswer(String checkpointId, String optionId, Verdict verdict, boolean confirmed,
                               boolean proceededDespiteWarning, int revisions, Instant answeredAt) {}
