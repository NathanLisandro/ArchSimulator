package dev.archsim.domain.logquery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SPL/Logs-Insights-flavoured language:
 *   [search terms] [| stats ...] [| timechart span=1m ...] [| where ...] [| sort ...] [| head n] [| fields a, b]
 */
public final class LogQueryParser {
    private static final Pattern COMPARISON = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\s*(!=|>=|<=|=|>|<)\\s*(.+)$");
    private static final Pattern AGG = Pattern.compile("(?i)^(count|sum|avg|min|max|p50|p90|p95|p99|dc|distinct_count)\\s*(?:\\(\\s*([A-Za-z_][A-Za-z0-9_.]*)?\\s*\\))?$");
    private static final Pattern SPAN = Pattern.compile("(?i)^span\\s*=\\s*(\\d+)(s|m|h|d)$");

    public LogQuery parse(String query) {
        String q = query == null ? "" : query.trim();
        List<String> stages = splitPipes(q);
        LogFilter filter = parseSearch(stages.isEmpty() ? "" : stages.getFirst());
        List<Stage> pipeline = new ArrayList<>();
        for (int i = 1; i < stages.size(); i++) pipeline.add(parseStage(stages.get(i)));
        return new LogQuery(filter, List.copyOf(pipeline));
    }

    private static List<String> splitPipes(String q) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : q.toCharArray()) {
            if (c == '"') quoted = !quoted;
            if (c == '|' && !quoted) { out.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        out.add(current.toString().trim());
        return out;
    }

    LogFilter parseSearch(String search) {
        List<String> tokens = tokenize(search);
        if (tokens.isEmpty()) return new LogFilter.All();
        return parseOr(tokens, new int[]{0});
    }

    private LogFilter parseOr(List<String> tokens, int[] pos) {
        List<LogFilter> parts = new ArrayList<>();
        parts.add(parseAnd(tokens, pos));
        while (pos[0] < tokens.size() && tokens.get(pos[0]).equalsIgnoreCase("OR")) {
            pos[0]++;
            parts.add(parseAnd(tokens, pos));
        }
        return parts.size() == 1 ? parts.getFirst() : new LogFilter.Or(List.copyOf(parts));
    }

    private LogFilter parseAnd(List<String> tokens, int[] pos) {
        List<LogFilter> parts = new ArrayList<>();
        while (pos[0] < tokens.size() && !tokens.get(pos[0]).equalsIgnoreCase("OR") && !tokens.get(pos[0]).equals(")")) {
            String token = tokens.get(pos[0]);
            if (token.equalsIgnoreCase("AND")) { pos[0]++; continue; }
            parts.add(parseUnary(tokens, pos));
        }
        if (parts.isEmpty()) throw new LogQueryException("Expressão de busca incompleta");
        return parts.size() == 1 ? parts.getFirst() : new LogFilter.And(List.copyOf(parts));
    }

    private LogFilter parseUnary(List<String> tokens, int[] pos) {
        String token = tokens.get(pos[0]);
        if (token.equalsIgnoreCase("NOT") || token.equals("!")) { pos[0]++; return new LogFilter.Not(parseUnary(tokens, pos)); }
        if (token.equals("(")) {
            pos[0]++;
            LogFilter inner = parseOr(tokens, pos);
            if (pos[0] >= tokens.size() || !tokens.get(pos[0]).equals(")")) throw new LogQueryException("Parêntese não fechado na busca");
            pos[0]++;
            return inner;
        }
        pos[0]++;
        return atom(token);
    }

    private LogFilter atom(String token) {
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) return new LogFilter.Term(token.substring(1, token.length() - 1));
        Matcher m = COMPARISON.matcher(token);
        if (m.matches()) {
            LogFilter.Comparison.Op op = switch (m.group(2)) {
                case "=" -> LogFilter.Comparison.Op.EQ;
                case "!=" -> LogFilter.Comparison.Op.NE;
                case ">" -> LogFilter.Comparison.Op.GT;
                case ">=" -> LogFilter.Comparison.Op.GTE;
                case "<" -> LogFilter.Comparison.Op.LT;
                default -> LogFilter.Comparison.Op.LTE;
            };
            return new LogFilter.Comparison(m.group(1), op, unquote(m.group(3)));
        }
        return new LogFilter.Term(token);
    }

    private static String unquote(String v) {
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    /** Collapses "count > 100" into "count>100" so a comparison stays a single token. */
    static String normalizeOperators(String text) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            if (!quoted && (c == '=' || c == '<' || c == '>' || c == '!')) {
                while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') out.deleteCharAt(out.length() - 1);
                out.append(c);
                if (i + 1 < text.length() && text.charAt(i + 1) == '=') { out.append('='); i++; }
                while (i + 1 < text.length() && text.charAt(i + 1) == ' ') i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    static List<String> tokenize(String text) {
        text = normalizeOperators(text);
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') { quoted = !quoted; current.append(c); continue; }
            if (!quoted && (c == '(' || c == ')')) {
                if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); }
                tokens.add(String.valueOf(c));
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                if (!current.isEmpty()) { tokens.add(current.toString()); current.setLength(0); }
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private Stage parseStage(String stage) {
        if (stage.isBlank()) throw new LogQueryException("Etapa vazia no pipeline");
        String[] head = stage.split("\\s+", 2);
        String command = head[0].toLowerCase(Locale.ROOT);
        String rest = head.length > 1 ? head[1].trim() : "";
        return switch (command) {
            case "stats" -> parseStats(rest, null);
            case "timechart" -> parseTimechart(rest);
            case "sort" -> new Stage.Sort(parseSortKeys(rest));
            case "head", "limit" -> new Stage.Head(parseInt(rest, command));
            case "fields", "table" -> new Stage.Fields(splitCommas(rest));
            case "where" -> new Stage.Where(parseSearch(rest));
            case "dedup" -> new Stage.Dedup(splitCommas(rest));
            default -> throw new LogQueryException("Comando desconhecido no pipeline: '" + head[0] + "'. Use stats, timechart, where, sort, head, fields ou dedup.");
        };
    }

    private Stage.Stats parseStats(String rest, Duration span) {
        String aggPart = rest;
        List<String> by = List.of();
        Matcher byMatcher = Pattern.compile("(?i)\\s+by\\s+").matcher(rest);
        if (byMatcher.find()) {
            aggPart = rest.substring(0, byMatcher.start()).trim();
            by = splitCommas(rest.substring(byMatcher.end()));
        }
        List<Aggregation> aggs = new ArrayList<>();
        for (String expr : splitCommas(aggPart)) {
            String alias = null;
            Matcher as = Pattern.compile("(?i)^(.*?)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)$").matcher(expr);
            if (as.matches()) { expr = as.group(1).trim(); alias = as.group(2); }
            Matcher m = AGG.matcher(expr.trim());
            if (!m.matches()) throw new LogQueryException("Agregação inválida: '" + expr + "'. Use count, sum(x), avg(x), min(x), max(x), p50/p90/p95/p99(x) ou dc(x).");
            aggs.add(new Aggregation(m.group(1).toLowerCase(Locale.ROOT), m.group(2), alias == null ? expr.trim() : alias));
        }
        if (aggs.isEmpty()) aggs.add(new Aggregation("count", null, "count"));
        return new Stage.Stats(List.copyOf(aggs), by, span);
    }

    private Stage parseTimechart(String rest) {
        Duration span = Duration.ofMinutes(1);
        String remainder = rest;
        String[] parts = rest.split("\\s+", 2);
        Matcher m = SPAN.matcher(parts[0]);
        if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            span = switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> Duration.ofSeconds(n);
                case "h" -> Duration.ofHours(n);
                case "d" -> Duration.ofDays(n);
                default -> Duration.ofMinutes(n);
            };
            remainder = parts.length > 1 ? parts[1] : "";
        }
        Stage.Stats stats = parseStats(remainder, span);
        return new Stage.Timechart(stats.aggregations(), stats.groupBy(), span);
    }

    private static List<Stage.SortKey> parseSortKeys(String rest) {
        List<Stage.SortKey> keys = new ArrayList<>();
        for (String raw : splitCommas(rest)) {
            String f = raw.trim();
            boolean desc = false;
            if (f.startsWith("-")) { desc = true; f = f.substring(1).trim(); }
            else if (f.startsWith("+")) f = f.substring(1).trim();
            Matcher m = Pattern.compile("(?i)^(.*?)\\s+(asc|desc)$").matcher(f);
            if (m.matches()) { f = m.group(1).trim(); desc = m.group(2).equalsIgnoreCase("desc"); }
            if (f.isEmpty()) throw new LogQueryException("sort precisa de um campo");
            keys.add(new Stage.SortKey(f, desc));
        }
        if (keys.isEmpty()) throw new LogQueryException("sort precisa de um campo");
        return List.copyOf(keys);
    }

    private static int parseInt(String rest, String command) {
        try { return Integer.parseInt(rest.trim()); }
        catch (NumberFormatException e) { throw new LogQueryException(command + " precisa de um número, ex.: | " + command + " 20"); }
    }

    /** Splits on top-level commas; when there is no comma, on whitespace (so "| fields a b" also works). */
    private static List<String> splitCommas(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        if (text.indexOf(',') < 0) {
            if (text.matches("(?i).*\\bas\\b.*") || text.indexOf('(') >= 0) { out.add(text.trim()); return out; }
            for (String part : text.split("\\s+")) if (!part.isBlank()) out.add(part.trim());
            return out;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) { if (!current.toString().isBlank()) out.add(current.toString().trim()); current.setLength(0); continue; }
            current.append(c);
        }
        if (!current.toString().isBlank()) out.add(current.toString().trim());
        return out;
    }
}
