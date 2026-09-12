package dev.archsim.domain.template;

/** "When param <op> value, recommend pathId because reason". Ops: >, >=, <, <=, ==, != (numeric) or 'eq' (string). */
public record RecommendationRule(String param, String op, double value, String pathId, String reason) {
    public boolean applies(String actual) {
        if (actual == null) return false;
        if (op.equals("eq")) return actual.equals(String.valueOf(value));
        double a;
        try { a = Double.parseDouble(actual); } catch (NumberFormatException e) { return false; }
        return switch (op) {
            case ">" -> a > value;
            case ">=" -> a >= value;
            case "<" -> a < value;
            case "<=" -> a <= value;
            case "==" -> a == value;
            case "!=" -> a != value;
            default -> throw new IllegalArgumentException("Unknown op " + op);
        };
    }
}
