package dev.archsim.domain.shared;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders {{name}} and {{name|filter}} placeholders. Filters: number (pt-BR grouping), compact (4,3K / 81,2M). */
public final class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*(?:\\|\\s*([a-zA-Z]+)\\s*)?}}");

    public String render(String template, Map<String, String> params) {
        if (template == null) return null;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String filter = m.group(2);
            String value = params.get(key);
            if (value == null) throw new IllegalArgumentException("Unknown template parameter: " + key);
            m.appendReplacement(out, Matcher.quoteReplacement(applyFilter(value, filter)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String applyFilter(String value, String filter) {
        if (filter == null) return value;
        return switch (filter) {
            case "number" -> groupThousands(value);
            case "compact" -> compact(value);
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            default -> throw new IllegalArgumentException("Unknown filter: " + filter);
        };
    }

    private static String groupThousands(String value) {
        long n = Long.parseLong(value.trim());
        String digits = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append('.');
            sb.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    private static String compact(String value) {
        double n = Double.parseDouble(value.trim());
        if (n >= 1_000_000_000) return oneDecimal(n / 1_000_000_000) + "B";
        if (n >= 1_000_000) return oneDecimal(n / 1_000_000) + "M";
        if (n >= 1_000) return oneDecimal(n / 1_000) + "K";
        return Long.toString(Math.round(n));
    }

    /** 210,0K lê mal: uma casa decimal só aparece quando diz alguma coisa. */
    private static String oneDecimal(double v) {
        long scaled = Math.round(v * 10);
        return scaled % 10 == 0 ? String.valueOf(scaled / 10) : (scaled / 10) + "," + (scaled % 10);
    }
}
