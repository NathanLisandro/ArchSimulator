package dev.archsim.domain.cases;

import java.util.List;

/** Structured fields the user fills in; pathFieldId names the field whose value identifies the SolutionPath followed. */
public record AnswerForm(List<FieldSpec> fields, String pathFieldId) {}
