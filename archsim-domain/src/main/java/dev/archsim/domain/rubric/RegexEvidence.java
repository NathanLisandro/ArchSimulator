package dev.archsim.domain.rubric;

import java.util.regex.Pattern;

public record RegexEvidence(String field, String pattern) implements Evidence {
    @Override public boolean matches(AnswerContext ctx) {
        String text = field == null ? ctx.allText() : ctx.text(field);
        return Pattern.compile(pattern, Pattern.DOTALL).matcher(text).find();
    }
    @Override public String describe() { return "padrão " + pattern; }
}
