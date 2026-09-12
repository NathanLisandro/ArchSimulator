package dev.archsim.domain.logquery;

import dev.archsim.domain.observability.LogEntry;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Search-stage predicates: field comparison, quoted phrase, bare term, and the boolean combinators. */
sealed interface LogFilter extends Predicate<LogEntry> {

    record Comparison(String field, Op op, String value) implements LogFilter {
        enum Op { EQ, NE, GT, GTE, LT, LTE }

        @Override public boolean test(LogEntry e) {
            if (op == Op.GT || op == Op.GTE || op == Op.LT || op == Op.LTE) {
                Double actual = FieldAccess.number(e, field);
                if (actual == null) return false;
                double expected;
                try { expected = Double.parseDouble(value); } catch (NumberFormatException ex) { return false; }
                return switch (op) {
                    case GT -> actual > expected;
                    case GTE -> actual >= expected;
                    case LT -> actual < expected;
                    case LTE -> actual <= expected;
                    default -> false;
                };
            }
            String actual = FieldAccess.text(e, field);
            boolean matches = actual != null && matchesGlob(actual, value);
            return op == Op.EQ ? matches : !matches;
        }

        private static boolean matchesGlob(String actual, String pattern) {
            String a = actual.toLowerCase(Locale.ROOT);
            String p = pattern.toLowerCase(Locale.ROOT);
            if (p.indexOf('*') < 0) return a.equals(p);
            StringBuilder regex = new StringBuilder();
            for (char c : p.toCharArray()) regex.append(c == '*' ? ".*" : java.util.regex.Pattern.quote(String.valueOf(c)));
            return a.matches(regex.toString());
        }
    }

    record Term(String text) implements LogFilter {
        @Override public boolean test(LogEntry e) { return FieldAccess.raw(e).toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)); }
    }

    record And(List<LogFilter> parts) implements LogFilter {
        @Override public boolean test(LogEntry e) { return parts.stream().allMatch(p -> p.test(e)); }
    }

    record Or(List<LogFilter> parts) implements LogFilter {
        @Override public boolean test(LogEntry e) { return parts.stream().anyMatch(p -> p.test(e)); }
    }

    record Not(LogFilter inner) implements LogFilter {
        @Override public boolean test(LogEntry e) { return !inner.test(e); }
    }

    record All() implements LogFilter {
        @Override public boolean test(LogEntry e) { return true; }
    }
}
