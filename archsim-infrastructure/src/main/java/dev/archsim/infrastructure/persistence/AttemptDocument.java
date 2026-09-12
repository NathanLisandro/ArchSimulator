package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.attempt.*;

import java.time.Instant;
import java.util.List;

/** JSON body of an attempt row: everything that is not an indexed column. */
record AttemptDocument(List<CheckpointAnswer> checkpointAnswers, InvestigationTrail trail, FinalAnswer finalAnswer, Evaluation evaluation,
                       List<MitigationEffectRecord> mitigations, List<StatusUpdate> statusUpdates, Instant resolvedAt) {
    static AttemptDocument from(Attempt a) {
        return new AttemptDocument(a.checkpointAnswers(), a.trail(), a.finalAnswer().orElse(null), a.evaluation().orElse(null),
                a.appliedMitigations(), a.statusUpdates(), a.resolvedAt().orElse(null));
    }
}
