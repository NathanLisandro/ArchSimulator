package dev.archsim.domain.template;

import java.util.Map;

/** Tiny recursive-descent evaluator: + - * / ( ), numbers, parameter names, round(x), floor(x), ceil(x), max(a,b), min(a,b). */
public final class ArithmeticExpression {
    private final String src;
    private final Map<String, String> params;
    private int pos;

    private ArithmeticExpression(String src, Map<String, String> params) { this.src = src; this.params = params; }

    public static long evaluate(String expression, Map<String, String> params) {
        return Math.round(evaluateDouble(expression, params));
    }

    public static double evaluateDouble(String expression, Map<String, String> params) {
        ArithmeticExpression e = new ArithmeticExpression(expression.replace(" ", ""), params);
        double v = e.expr();
        if (e.pos != e.src.length()) throw new IllegalArgumentException("Unexpected token at " + e.pos + " in " + expression);
        return v;
    }

    private double expr() {
        double v = term();
        while (pos < src.length() && (peek() == '+' || peek() == '-')) {
            char op = src.charAt(pos++);
            double r = term();
            v = op == '+' ? v + r : v - r;
        }
        return v;
    }

    private double term() {
        double v = factor();
        while (pos < src.length() && (peek() == '*' || peek() == '/')) {
            char op = src.charAt(pos++);
            double r = factor();
            v = op == '*' ? v * r : v / r;
        }
        return v;
    }

    private double factor() {
        if (peek() == '(') { pos++; double v = expr(); expect(')'); return v; }
        if (peek() == '-') { pos++; return -factor(); }
        if (Character.isDigit(peek()) || peek() == '.') {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(peek()) || peek() == '.' || peek() == '_')) pos++;
            return Double.parseDouble(src.substring(start, pos).replace("_", ""));
        }
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) pos++;
        String name = src.substring(start, pos);
        if (name.isEmpty()) throw new IllegalArgumentException("Unexpected char '" + peek() + "' in " + src);
        if (pos < src.length() && peek() == '(') {
            pos++;
            double a = expr();
            double b = 0;
            if (peek() == ',') { pos++; b = expr(); }
            expect(')');
            return switch (name) {
                case "round" -> Math.round(a);
                case "floor" -> Math.floor(a);
                case "ceil" -> Math.ceil(a);
                case "max" -> Math.max(a, b);
                case "min" -> Math.min(a, b);
                default -> throw new IllegalArgumentException("Unknown function " + name);
            };
        }
        String value = params.get(name);
        if (value == null) throw new IllegalArgumentException("Unknown parameter " + name);
        return Double.parseDouble(value);
    }

    private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }
    private void expect(char c) { if (peek() != c) throw new IllegalArgumentException("Expected '" + c + "' at " + pos + " in " + src); pos++; }
}
