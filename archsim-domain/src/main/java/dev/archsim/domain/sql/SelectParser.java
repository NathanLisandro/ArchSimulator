package dev.archsim.domain.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tolerant, regex-based parser for the subset of SELECT that the EXPLAIN simulator understands. */
public final class SelectParser {
    private static final Pattern SELECT = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+(.*)$");
    private static final Pattern TABLE = Pattern.compile("(?i)^\\s*([a-z_][a-z0-9_.]*)(?:\\s+(?:as\\s+)?(?!on\\b|where\\b|join\\b|left\\b|inner\\b|order\\b|group\\b|limit\\b)([a-z_][a-z0-9_]*))?");
    private static final Pattern JOIN_SPLIT = Pattern.compile("(?i)\\s+(?:left\\s+|right\\s+|inner\\s+|outer\\s+)*join\\s+");
    private static final Pattern ON = Pattern.compile("(?i)\\s+on\\s+([a-z0-9_.]+)\\s*=\\s*([a-z0-9_.]+)");
    private static final Pattern PREDICATE = Pattern.compile(
            "(?i)^\\s*(?<fn>[a-z_]+\\s*\\(\\s*)?(?<col>[a-z_][a-z0-9_.]*)(?<cast>\\s*::\\s*[a-z]+)?\\s*\\)?\\s*(?<op>>=|<=|<>|!=|=|>|<|\\bnot\\s+like\\b|\\blike\\b|\\bilike\\b|\\bin\\b|\\bis\\s+null\\b|\\bis\\s+not\\s+null\\b|\\bbetween\\b)\\s*(?<lit>.*)$");
    private static final Pattern PARAM = Pattern.compile("^(\\$\\d+|\\?|:[a-z_0-9]+)$");

    public SelectStatement parse(String sql) {
        String cleaned = sql.trim().replaceAll(";\\s*$", "").replaceAll("\\s+", " ");
        Matcher m = SELECT.matcher(cleaned);
        if (!m.matches()) throw new SqlParseException("Only SELECT statements are supported");
        String selectList = m.group(1).trim();
        String rest = m.group(2);

        String fromPart = cutBefore(rest, "(?i)\\s+where\\s+|\\s+order\\s+by\\s+|\\s+group\\s+by\\s+|\\s+limit\\s+|\\s+fetch\\s+first\\s+");
        String wherePart = between(rest, "(?i)\\s+where\\s+", "(?i)\\s+order\\s+by\\s+|\\s+group\\s+by\\s+|\\s+limit\\s+|\\s+fetch\\s+first\\s+");
        String orderPart = between(rest, "(?i)\\s+order\\s+by\\s+", "(?i)\\s+limit\\s+|\\s+fetch\\s+first\\s+");
        Integer limit = extractLimit(rest);

        List<TableRef> tables = new ArrayList<>();
        List<JoinCondition> joins = new ArrayList<>();
        String[] segments = JOIN_SPLIT.split(fromPart);
        for (String seg : segments) {
            Matcher on = ON.matcher(seg);
            String tablePart = seg;
            if (on.find()) {
                tablePart = seg.substring(0, on.start());
                joins.add(new JoinCondition(columnRef(on.group(1)), columnRef(on.group(2))));
            }
            for (String t : tablePart.split(",")) {
                Matcher tm = TABLE.matcher(t.trim());
                if (tm.find()) tables.add(new TableRef(tm.group(1).toLowerCase(), tm.group(2) == null ? null : tm.group(2).toLowerCase()));
            }
        }

        List<Predicate> predicates = new ArrayList<>();
        if (wherePart != null) {
            for (String clause : wherePart.split("(?i)\\s+and\\s+")) {
                Matcher pm = PREDICATE.matcher(clause.trim());
                if (!pm.find()) { predicates.add(new Predicate(null, Operator.OTHER, null, false, false, clause.trim())); continue; }
                boolean fn = pm.group("fn") != null || pm.group("cast") != null;
                String op = pm.group("op").toLowerCase().replaceAll("\\s+", " ");
                String rawLit = pm.group("lit").trim();
                String lit = stripQuotes(rawLit);
                boolean quoted = !lit.equals(rawLit);
                predicates.add(new Predicate(columnRef(pm.group("col")), operatorFor(op, lit), lit, quoted, fn, clause.trim()));
            }
        }

        List<OrderBy> orderBy = new ArrayList<>();
        if (orderPart != null) {
            for (String o : orderPart.split(",")) {
                String[] parts = o.trim().split("\\s+");
                orderBy.add(new OrderBy(columnRef(parts[0]), parts.length > 1 && parts[1].equalsIgnoreCase("desc")));
            }
        }
        boolean aggregate = selectList.matches("(?is).*\\b(count|sum|avg|min|max)\\s*\\(.*") || rest.matches("(?is).*\\s+group\\s+by\\s+.*");
        return new SelectStatement(List.copyOf(tables), List.copyOf(joins), List.copyOf(predicates), List.copyOf(orderBy), limit, aggregate, selectList);
    }

    private static Operator operatorFor(String op, String literal) {
        return switch (op) {
            case "=" -> Operator.EQ;
            case ">", "<", ">=", "<=", "between" -> Operator.RANGE;
            case "<>", "!=", "not like" -> Operator.NOT_EQ;
            case "in" -> Operator.IN;
            case "like", "ilike" -> literal.startsWith("%") ? Operator.LIKE_CONTAINS : Operator.LIKE_PREFIX;
            case "is null", "is not null" -> Operator.IS_NULL;
            default -> Operator.OTHER;
        };
    }

    private static ColumnRef columnRef(String raw) {
        String[] parts = raw.toLowerCase().split("\\.");
        return parts.length == 2 ? new ColumnRef(parts[0], parts[1]) : new ColumnRef(null, parts[parts.length - 1]);
    }

    private static String stripQuotes(String lit) {
        if (PARAM.matcher(lit).matches()) return lit;
        if (lit.length() >= 2 && lit.startsWith("'") && lit.endsWith("'")) return lit.substring(1, lit.length() - 1);
        return lit;
    }

    private static Integer extractLimit(String rest) {
        Matcher lm = Pattern.compile("(?i)\\s+limit\\s+(\\d+)").matcher(rest);
        if (lm.find()) return Integer.parseInt(lm.group(1));
        Matcher fm = Pattern.compile("(?i)\\s+fetch\\s+first\\s+(\\d+)\\s+rows").matcher(rest);
        if (fm.find()) return Integer.parseInt(fm.group(1));
        Matcher rn = Pattern.compile("(?i)rownum\\s*<=?\\s*(\\d+)").matcher(rest);
        if (rn.find()) return Integer.parseInt(rn.group(1));
        return null;
    }

    private static String cutBefore(String text, String stopRegex) {
        Matcher m = Pattern.compile(stopRegex).matcher(text);
        return m.find() ? text.substring(0, m.start()) : text;
    }

    private static String between(String text, String startRegex, String stopRegex) {
        Matcher s = Pattern.compile(startRegex).matcher(text);
        if (!s.find()) return null;
        String after = text.substring(s.end());
        return cutBefore(after, stopRegex);
    }
}
