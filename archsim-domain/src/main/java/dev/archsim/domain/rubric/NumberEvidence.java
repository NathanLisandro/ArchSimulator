package dev.archsim.domain.rubric;

/**
 * Matches when a numeric field is within relativeTolerance of the expected value (0.25 = "same order of magnitude,
 * give or take 25%"). Accepts pt-BR formatting (4.100 / 4,1) and ignores units the user may have typed.
 */
public record NumberEvidence(String field, String expected, double relativeTolerance) implements Evidence {
    public NumberEvidence(String field, double expected, double relativeTolerance) {
        this(field, String.valueOf(expected), relativeTolerance);
    }

    @Override public boolean matches(AnswerContext ctx) {
        Double actual = parse(ctx.text(field));
        Double target = parse(expected);
        if (actual == null || target == null) return false;
        double tolerance = Math.abs(target * Math.max(0.0001, relativeTolerance));
        return Math.abs(actual - target) <= tolerance;
    }

    /** "4.100 rps" -> 4100.0; "1,5" -> 1.5; "muitos" -> null. */
    static Double parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replaceAll("[^0-9.,-]", "");
        if (cleaned.isBlank()) return null;
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            // Both present: the rightmost separator is the decimal one.
            cleaned = lastComma > lastDot ? cleaned.replace(".", "").replace(',', '.') : cleaned.replace(",", "");
        } else if (lastComma >= 0) {
            cleaned = cleaned.replace(',', '.');                       // pt-BR decimal comma
        } else if (lastDot >= 0 && cleaned.length() - lastDot - 1 == 3 && lastDot > 0) {
            cleaned = cleaned.replace(".", "");                        // pt-BR thousands dot: 4.100
        }
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException e) { return null; }
    }

    @Override public String describe() { return field + " ≈ " + expected + " (±" + Math.round(relativeTolerance * 100) + "%)"; }
}
