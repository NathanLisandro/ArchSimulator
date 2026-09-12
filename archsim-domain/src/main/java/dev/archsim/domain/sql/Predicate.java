package dev.archsim.domain.sql;

public record Predicate(ColumnRef column, Operator operator, String literal, boolean quotedLiteral, boolean wrappedInFunction, String rawText) {
    /** True when the literal is an unquoted number (a candidate for an implicit cast on textual columns). */
    public boolean literalIsNumeric() {
        return !quotedLiteral && literal != null && literal.matches("-?\\d+(\\.\\d+)?");
    }
}
