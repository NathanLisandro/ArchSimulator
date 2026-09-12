package dev.archsim.domain.evaluation;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.Evaluation;
import dev.archsim.domain.cases.Case;

import java.time.Instant;

/** Port: produces an Evaluation for an attempt whose final answer is set. The only implementation today is rubric-based (offline). */
@FunctionalInterface
public interface CaseEvaluator {
    Evaluation evaluate(Case theCase, Attempt attempt, Instant now);
}
