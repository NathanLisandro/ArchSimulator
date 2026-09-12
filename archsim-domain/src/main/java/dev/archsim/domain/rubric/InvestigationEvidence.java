package dev.archsim.domain.rubric;

import dev.archsim.domain.console.ConsoleActionKind;

/** Matches when the investigation trail has at least minCount actions of the kind (optionally targeting something). */
public record InvestigationEvidence(ConsoleActionKind kind, String targetContains, int minCount) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) {
        return ctx.investigationCount(kind, targetContains) >= Math.max(1, minCount);
    }
    @Override public String describe() { return "investigou via " + kind + (targetContains == null ? "" : " (" + targetContains + ")"); }
}
