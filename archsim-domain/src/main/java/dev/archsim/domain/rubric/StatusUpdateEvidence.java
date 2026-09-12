package dev.archsim.domain.rubric;

/** Matches when at least minCount status updates were posted, the first within firstWithinSeconds of the start. */
public record StatusUpdateEvidence(int minCount, long firstWithinSeconds) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) { return ctx.statusUpdates(minCount, firstWithinSeconds); }
    @Override public String describe() { return "comunicou " + minCount + " atualizações, a primeira em até " + firstWithinSeconds + "s"; }
}
