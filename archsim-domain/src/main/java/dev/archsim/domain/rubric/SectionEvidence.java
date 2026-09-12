package dev.archsim.domain.rubric;

import java.util.List;
import java.util.Locale;

/** Matches when every listed heading is present in the markdown (case-insensitive, prefix match). */
public record SectionEvidence(List<String> headings) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) {
        List<String> present = ctx.markdownHeadings().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        return headings.stream().allMatch(h -> present.stream().anyMatch(p -> p.startsWith(h.toLowerCase(Locale.ROOT))));
    }
    @Override public String describe() { return "seções " + String.join(", ", headings); }
}
