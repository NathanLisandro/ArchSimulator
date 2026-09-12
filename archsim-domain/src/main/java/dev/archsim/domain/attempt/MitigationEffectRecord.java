package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.cases.Verdict;

import java.time.Instant;

/** One mitigation the responder applied. informed=false means it was fired before any investigation. */
public record MitigationEffectRecord(String mitigationId, Verdict verdict, MitigationEffect effect, boolean informed, Instant appliedAt) {}
