package dev.archsim.domain.rubric;

import java.util.List;
import java.util.Locale;

/** Matches when at least minMatches of the keywords appear (case-insensitive) in the field (or in all text when field is null). */
public record KeywordEvidence(String field, List<String> anyOf, int minMatches) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) {
        String text = (field == null ? ctx.allText() : ctx.text(field)).toLowerCase(Locale.ROOT);
        long hits = anyOf.stream().map(k -> k.toLowerCase(Locale.ROOT)).filter(text::contains).count();
        return hits >= Math.max(1, minMatches);
    }
    @Override public String describe() { return "menciona " + String.join(" / ", anyOf); }
}
