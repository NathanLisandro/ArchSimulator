package dev.archsim.domain.rubric;

public sealed interface Evidence permits KeywordEvidence, RegexEvidence, SectionEvidence, ChoiceEvidence, InvestigationEvidence, MitigationEvidence, StatusUpdateEvidence, GraphEvidence, NumberEvidence {
    boolean matches(AnswerContext ctx);
    String describe();
}
