package dev.archsim.domain.rubric;

import java.util.List;

public record ChoiceEvidence(String field, List<String> anyOf) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) {
        return ctx.choices(field).stream().anyMatch(anyOf::contains);
    }
    @Override public String describe() { return field + " ∈ " + anyOf; }
}
