package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.cases.Verdict;

/** What the console shows right after a mitigation is applied. */
public record MitigationOutcome(String mitigationId, Verdict verdict, MitigationEffect effect, String message, boolean incidentResolved) {}
