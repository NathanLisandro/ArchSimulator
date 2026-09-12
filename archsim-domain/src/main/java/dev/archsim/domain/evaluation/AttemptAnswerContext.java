package dev.archsim.domain.evaluation;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.FinalAnswer;
import dev.archsim.domain.attempt.MitigationEffectRecord;
import dev.archsim.domain.attempt.StatusUpdate;
import dev.archsim.domain.cases.AnswerForm;
import dev.archsim.domain.cases.FieldType;
import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.rubric.AnswerContext;
import dev.archsim.domain.rubric.GraphEvidence;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AttemptAnswerContext implements AnswerContext {
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private final Attempt attempt;
    private final FinalAnswer answer;
    private final AnswerForm form;

    AttemptAnswerContext(Attempt attempt, AnswerForm form) {
        this.attempt = attempt;
        this.form = form;
        this.answer = attempt.finalAnswer().orElseThrow(() -> new IllegalStateException("Attempt has no final answer"));
    }

    /** MARKDOWN fields resolve to the free-text markdown when not sent as a structured value. */
    @Override public String text(String field) {
        if (field == null || field.equals("markdown")) return answer.markdown();
        if (field.equals("mermaid")) return answer.mermaid() == null ? "" : answer.mermaid();
        String value = answer.fields().value(field);
        if (value.isBlank() && isMarkdownField(field)) return answer.markdown();
        return value;
    }

    private boolean isMarkdownField(String field) {
        return form.fields().stream().anyMatch(f -> f.id().equals(field) && f.type() == FieldType.MARKDOWN);
    }

    @Override public String allText() {
        StringBuilder sb = new StringBuilder(answer.markdown()).append('\n');
        answer.fields().values().values().forEach(v -> sb.append(v).append('\n'));
        if (answer.mermaid() != null) sb.append(answer.mermaid());
        return sb.toString();
    }

    @Override public List<String> choices(String field) { return answer.fields().valuesOf(field); }

    @Override public List<String> markdownHeadings() {
        Matcher m = HEADING.matcher(answer.markdown());
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) out.add(m.group(1).trim());
        return out;
    }

    @Override public int investigationCount(ConsoleActionKind kind, String targetContains) {
        return attempt.trail().count(kind, targetContains);
    }

    @Override public boolean appliedMitigation(String mitigationId, MitigationEffect effect, boolean requireInformed) {
        return attempt.appliedMitigations().stream()
                .filter(m -> mitigationId == null || m.mitigationId().equals(mitigationId))
                .filter(m -> effect == null || m.effect() == effect)
                .anyMatch(m -> !requireInformed || m.informed());
    }

    @Override public boolean graphMatches(GraphEvidence evidence) {
        return DrawnArchitecture.of(answer.graph(), answer.mermaid()).matches(evidence);
    }

    @Override public boolean statusUpdates(int minCount, long firstWithinSeconds) {
        List<StatusUpdate> updates = attempt.statusUpdates();
        if (updates.size() < Math.max(1, minCount)) return false;
        return firstWithinSeconds <= 0 || updates.getFirst().elapsedSeconds() <= firstWithinSeconds;
    }
}
