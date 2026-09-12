package dev.archsim.domain.rubric;

import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.console.ConsoleActionKind;

import java.util.List;

/** What evidence checks can see about an answer. Implemented by the evaluation layer over an Attempt. */
public interface AnswerContext {
    /** Text of a structured field, or of the free markdown when field is null. Never null. */
    String text(String field);
    /** Every textual input concatenated (fields + markdown). */
    String allText();
    List<String> choices(String field);
    List<String> markdownHeadings();
    int investigationCount(ConsoleActionKind kind, String targetContains);
    /** True when a mitigation with this id (or, when id is null, this effect) was applied, optionally after investigating. */
    boolean appliedMitigation(String mitigationId, MitigationEffect effect, boolean requireInformed);
    /** True when at least minCount status updates were posted and the first came within firstWithinSeconds. */
    boolean statusUpdates(int minCount, long firstWithinSeconds);
    /** Structural check over the drawn architecture (drag-and-drop graph, or Mermaid when there is no graph). */
    boolean graphMatches(GraphEvidence evidence);
}
