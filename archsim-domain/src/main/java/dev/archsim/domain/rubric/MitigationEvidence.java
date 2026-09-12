package dev.archsim.domain.rubric;

import dev.archsim.domain.cases.MitigationEffect;

/**
 * Matches when the responder applied a mitigation with the given id (or, when id is null, any mitigation with the
 * given effect). requireInformed demands that it was applied after some investigation.
 */
public record MitigationEvidence(String mitigationId, MitigationEffect effect, boolean requireInformed) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) { return ctx.appliedMitigation(mitigationId, effect, requireInformed); }
    @Override public String describe() {
        return "mitigou com " + (mitigationId != null ? mitigationId : effect) + (requireInformed ? " após investigar" : "");
    }
}
