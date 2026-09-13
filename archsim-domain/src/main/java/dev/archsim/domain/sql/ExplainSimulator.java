package dev.archsim.domain.sql;

import dev.archsim.domain.company.Column;
import dev.archsim.domain.company.DatabaseSchema;
import dev.archsim.domain.company.Index;
import dev.archsim.domain.company.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cost-based planner approximation. It does not execute anything: it reasons about table statistics and
 * indexes to produce a plausible plan and timing, in Postgres or Oracle text format.
 */
public final class ExplainSimulator {
    private static final double SEQ_MS_PER_ROW = 0.00005;      // 80M rows ≈ 4s
    private static final double INDEX_MS_PER_ROW = 0.02;
    private static final double SORT_MS_PER_ROW = 0.0006;
    private static final double LOW_SELECTIVITY_THRESHOLD = 0.04;

    private final SelectParser parser = new SelectParser();

    public ExplainPlan explain(String sql, DatabaseSchema schema, SqlDialect dialect) {
        SelectStatement stmt;
        try {
            stmt = parser.parse(stripExplain(sql));
        } catch (SqlParseException e) {
            return ExplainPlan.error(e.getMessage());
        }
        if (stmt.tables().isEmpty()) return ExplainPlan.error("syntax error: no FROM clause");
        for (TableRef ref : stmt.tables()) {
            if (schema.table(ref.name()).isEmpty()) {
                return ExplainPlan.error(dialect == SqlDialect.ORACLE
                        ? "ORA-00942: table or view does not exist (" + ref.name().toUpperCase() + ")"
                        : "relation \"" + ref.name() + "\" does not exist");
            }
        }

        PlanNode root = planTables(stmt, schema);
        if (stmt.aggregate()) {
            root = new PlanNode("Aggregate", null, null, List.of(), 1, root.cost() + root.estimatedRows() * 0.01, root.actualMs() + root.estimatedRows() * 0.0002, List.of(root));
        }
        if (stmt.limit() != null && !stmt.aggregate()) {
            long rows = Math.min(stmt.limit(), root.estimatedRows());
            root = new PlanNode("Limit", null, null, List.of(), rows, root.cost(), root.actualMs(), List.of(root));
        }
        String text = dialect == SqlDialect.ORACLE ? new OracleFormatter().format(root, stmt) : new PostgresFormatter().format(root);
        return new ExplainPlan(root, text, root.actualMs(), false);
    }

    private static String stripExplain(String sql) {
        return sql.trim().replaceFirst("(?is)^explain(\\s*\\([^)]*\\)|\\s+plan\\s+for|\\s+analyze|\\s+verbose)*\\s+", "");
    }

    private PlanNode planTables(SelectStatement stmt, DatabaseSchema schema) {
        List<PlanNode> scans = new ArrayList<>();
        for (TableRef ref : stmt.tables()) {
            Table table = schema.table(ref.name()).orElseThrow();
            scans.add(planScan(stmt, ref, table, false));
        }
        if (scans.size() == 1) return maybeSort(scans.getFirst(), stmt);

        // Join planning: drive from the most selective scan, then loop/hash into the others.
        PlanNode current = scans.stream().min((a, b) -> Long.compare(a.estimatedRows(), b.estimatedRows())).orElseThrow();
        List<PlanNode> remaining = new ArrayList<>(scans);
        remaining.remove(current);
        for (PlanNode next : remaining) {
            TableRef nextRef = stmt.tables().stream().filter(t -> t.name().equals(next.tableName())).findFirst().orElseThrow();
            Table nextTable = schema.table(nextRef.name()).orElseThrow();
            Optional<JoinCondition> join = stmt.joins().stream().filter(j -> touches(j, nextRef, stmt)).findFirst();
            Optional<Index> innerIndex = join.flatMap(j -> indexOnJoinColumn(nextTable, nextRef, j));
            if (innerIndex.isPresent() && current.estimatedRows() < nextTable.rowCount() / 10) {
                String cond = join.get().leftColumn().qualified() + " = " + join.get().rightColumn().qualified();
                long outerRows = current.estimatedRows();
                PlanNode inner = new PlanNode("Index Scan", nextTable.name(), innerIndex.get().name(),
                        List.of("Index Cond: (" + cond + ")"), 1, 0.56 + 4.0, INDEX_MS_PER_ROW, List.of())
                        .withAlias(nextRef.alias() == null ? "" : " " + nextRef.alias());
                double ms = current.actualMs() + outerRows * INDEX_MS_PER_ROW;
                current = new PlanNode("Nested Loop", null, null, List.of(), outerRows, current.cost() + outerRows * 4.5, ms, List.of(current, inner));
            } else {
                double ms = current.actualMs() + next.actualMs() + (current.estimatedRows() + next.estimatedRows()) * 0.0004;
                long rows = Math.max(current.estimatedRows(), next.estimatedRows());
                PlanNode hash = new PlanNode("Hash", null, null, List.of(), next.estimatedRows(), next.cost(), next.actualMs(), List.of(next));
                current = new PlanNode("Hash Join", null, null,
                        join.map(j -> List.of("Hash Cond: (" + j.leftColumn().qualified() + " = " + j.rightColumn().qualified() + ")")).orElse(List.of()),
                        rows, current.cost() + next.cost() + rows * 0.02, ms, List.of(current, hash));
            }
        }
        return maybeSort(current, stmt);
    }

    private static boolean touches(JoinCondition j, TableRef ref, SelectStatement stmt) {
        return ref.matches(j.leftColumn().alias()) || ref.matches(j.rightColumn().alias());
    }

    private static Optional<Index> indexOnJoinColumn(Table table, TableRef ref, JoinCondition j) {
        String col = ref.matches(j.leftColumn().alias()) ? j.leftColumn().column() : j.rightColumn().column();
        return table.indexes().stream().filter(i -> i.leadingColumn().equalsIgnoreCase(col)).findFirst();
    }

    private PlanNode maybeSort(PlanNode node, SelectStatement stmt) {
        if (stmt.orderBy().isEmpty() || node.details().contains("__ordered__")) return stripMarker(node);
        String keys = String.join(", ", stmt.orderBy().stream().map(o -> o.column().qualified() + (o.descending() ? " DESC" : "")).toList());
        long rows = node.estimatedRows();
        String method = stmt.limit() != null ? "Sort Method: top-N heapsort  Memory: " + Math.max(25, stmt.limit() * 2) + "kB"
                : rows > 200_000 ? "Sort Method: external merge  Disk: " + (rows / 40) + "kB" : "Sort Method: quicksort  Memory: " + Math.max(25, rows / 8) + "kB";
        return new PlanNode("Sort", null, null, List.of("Sort Key: " + keys, method), rows,
                node.cost() + rows * 0.02, node.actualMs() + rows * SORT_MS_PER_ROW, List.of(stripMarker(node)));
    }

    private static PlanNode stripMarker(PlanNode n) {
        if (!n.details().contains("__ordered__")) return n;
        return new PlanNode(n.operation(), n.target(), n.indexName(), n.details().stream().filter(d -> !d.equals("__ordered__")).toList(),
                n.estimatedRows(), n.cost(), n.actualMs(), n.children());
    }

    private PlanNode planScan(SelectStatement stmt, TableRef ref, Table table, boolean ignoreOrder) {
        List<Predicate> preds = stmt.predicates().stream().filter(p -> p.column() != null && ref.matches(p.column().alias())
                && (p.column().alias() != null || stmt.tables().size() == 1 || table.column(p.column().column()).isPresent())).toList();
        List<Predicate> sargable = preds.stream().filter(p -> isSargable(p, table)).toList();

        Index best = null; int bestMatched = 0; double bestSelectivity = 1.0; List<Predicate> bestConds = List.of();
        for (Index index : table.indexes()) {
            List<Predicate> conds = new ArrayList<>();
            double sel = 1.0;
            for (String col : index.columns()) {
                Optional<Predicate> eq = sargable.stream().filter(p -> p.column().column().equalsIgnoreCase(col) && p.operator() == Operator.EQ).findFirst();
                if (eq.isPresent()) { conds.add(eq.get()); sel *= selectivity(eq.get(), table); continue; }
                // Both ends of a range on the same column go into Index Cond, as Postgres does for `>= a AND < b`.
                List<Predicate> ranges = sargable.stream().filter(p -> p.column().column().equalsIgnoreCase(col)
                        && (p.operator() == Operator.RANGE || p.operator() == Operator.LIKE_PREFIX || p.operator() == Operator.IN)).toList();
                if (!ranges.isEmpty()) { conds.addAll(ranges); sel *= selectivity(ranges.getFirst(), table); }
                break;
            }
            boolean ordersByIndex = orderMatches(stmt, ref, index, conds);
            if (conds.isEmpty() && !(ordersByIndex && stmt.limit() != null && preds.isEmpty())) continue;
            boolean selectiveEnough = sel <= LOW_SELECTIVITY_THRESHOLD || (index.unique() && conds.size() == index.columns().size());
            if (!conds.isEmpty() && !selectiveEnough) continue;
            if (conds.size() > bestMatched || (conds.size() == bestMatched && sel < bestSelectivity)) {
                best = index; bestMatched = conds.size(); bestSelectivity = conds.isEmpty() ? 1.0 : sel; bestConds = conds;
            }
        }

        double totalSel = preds.stream().mapToDouble(p -> selectivity(p, table)).reduce(1.0, (a, b) -> a * b);
        long estRows = Math.max(1, Math.round(table.rowCount() * totalSel));
        List<Predicate> filters = new ArrayList<>(preds);
        filters.removeAll(bestConds);
        String aliasSuffix = ref.alias() == null ? "" : " " + ref.alias();

        if (best == null) {
            List<String> details = new ArrayList<>();
            if (!preds.isEmpty()) {
                details.add("Filter: " + renderFilter(preds, table));
                details.add("Rows Removed by Filter: " + (table.rowCount() - estRows));
            }
            double ms = table.rowCount() * SEQ_MS_PER_ROW + table.pages() * 0.0001;
            double cost = table.pages() + table.rowCount() * 0.01;
            return new PlanNode("Seq Scan", table.name(), null, details, estRows, cost, ms, List.of()).withAlias(aliasSuffix);
        }

        boolean ordered = orderMatches(stmt, ref, best, bestConds) || bestConds.isEmpty();
        boolean backward = ordered && !stmt.orderBy().isEmpty() && stmt.orderBy().getFirst().descending();
        long indexRows = bestConds.isEmpty() ? table.rowCount() : Math.max(1, Math.round(table.rowCount() * bestSelectivity));
        long fetched = (ordered && stmt.limit() != null) ? Math.min(indexRows, stmt.limit()) : indexRows;
        List<String> details = new ArrayList<>();
        if (!bestConds.isEmpty()) details.add("Index Cond: " + renderFilter(bestConds, table));
        if (!filters.isEmpty()) { details.add("Filter: " + renderFilter(filters, table)); details.add("Rows Removed by Filter: " + Math.max(0, indexRows - estRows)); }
        if (ordered) details.add("__ordered__");
        double ms = 0.3 + fetched * INDEX_MS_PER_ROW;
        double cost = 0.56 + fetched * 4.2;
        String op = (best.unique() && bestConds.size() == best.columns().size() && bestConds.stream().allMatch(p -> p.operator() == Operator.EQ))
                ? "Index Scan" : (backward ? "Index Scan Backward" : "Index Scan");
        long rows = Math.min(estRows, fetched);
        return new PlanNode(op, table.name(), best.name(), details, rows, cost, ms, List.of()).withAlias(aliasSuffix);
    }

    /** The index delivers rows in ORDER BY order when the order column is the next unconsumed index column,
     *  or the last consumed one when that condition was a range (equality prefix + range/order on same column). */
    private static boolean orderMatches(SelectStatement stmt, TableRef ref, Index index, List<Predicate> conds) {
        if (stmt.orderBy().isEmpty()) return false;
        OrderBy first = stmt.orderBy().getFirst();
        if (!ref.matches(first.column().alias())) return false;
        String orderCol = first.column().column();
        int consumed = conds.size();
        if (consumed < index.columns().size() && index.columns().get(consumed).equalsIgnoreCase(orderCol)) return true;
        return consumed > 0 && conds.getLast().operator() != Operator.EQ && index.columns().get(consumed - 1).equalsIgnoreCase(orderCol);
    }

    private static boolean isSargable(Predicate p, Table table) {
        if (p.wrappedInFunction() || p.operator() == Operator.OTHER || p.operator() == Operator.NOT_EQ || p.operator() == Operator.LIKE_CONTAINS) return false;
        Optional<Column> col = table.column(p.column().column());
        return col.map(c -> !(c.isTextual() && p.literalIsNumeric())).orElse(false);
    }

    private static double selectivity(Predicate p, Table table) {
        long distinct = table.column(p.column().column()).map(Column::distinctValues).orElse(100L);
        distinct = Math.max(1, distinct);
        return switch (p.operator()) {
            case EQ -> 1.0 / distinct;
            case IN -> Math.min(1.0, Math.max(1, p.literal() == null ? 1 : p.literal().split(",").length) / (double) distinct);
            case RANGE -> distinct > 1000 ? 0.1 : 0.33;
            case LIKE_PREFIX -> 0.01;
            case LIKE_CONTAINS -> 0.3;
            case NOT_EQ -> 1.0 - 1.0 / distinct;
            case IS_NULL -> 0.05;
            case OTHER -> 0.5;
        };
    }

    private static String renderFilter(List<Predicate> preds, Table table) {
        List<String> parts = new ArrayList<>();
        for (Predicate p : preds) {
            Optional<Column> col = table.column(p.column().column());
            if (col.isPresent() && col.get().isTextual() && p.literalIsNumeric()) {
                parts.add("((" + p.column().qualified() + ")::numeric = '" + p.literal() + "'::numeric)");
            } else {
                parts.add("(" + p.rawText().replaceAll("\\s+", " ") + ")");
            }
        }
        return parts.size() == 1 ? parts.getFirst() : "(" + String.join(" AND ", parts) + ")";
    }

    // ---------- formatters ----------

    static final class PostgresFormatter {
        String format(PlanNode root) {
            StringBuilder sb = new StringBuilder();
            write(root, sb, 0, true);
            sb.append(String.format(Locale.US, "Planning Time: %.3f ms%n", 0.15 + root.cost() / 1_000_000));
            sb.append(String.format(Locale.US, "Execution Time: %.3f ms%n", root.actualMs()));
            return sb.toString();
        }

        private void write(PlanNode n, StringBuilder sb, int indent, boolean first) {
            String pad = " ".repeat(indent);
            String head = first ? pad : pad + "->  ";
            String target = n.target() == null ? "" : " on " + n.target();
            String using = n.indexName() == null ? "" : " using " + n.indexName();
            double startup = n.usesIndex() ? 0.03 : 0.0;
            sb.append(String.format(Locale.US, "%s%s%s%s  (cost=%.2f..%.2f rows=%d width=%d) (actual time=%.3f..%.3f rows=%d loops=1)%n",
                    head, n.operation(), using, target, startup, n.cost(), n.estimatedRows(), 96, startup, n.actualMs(), n.estimatedRows()));
            int detailIndent = first ? indent + 2 : indent + 6;
            for (String d : n.details()) sb.append(" ".repeat(detailIndent)).append(d).append('\n');
            for (PlanNode c : n.children()) write(c, sb, first ? indent + 2 : indent + 6, false);
        }
    }

    static final class OracleFormatter {
        String format(PlanNode root, SelectStatement stmt) {
            List<String[]> rows = new ArrayList<>();
            List<String> predicates = new ArrayList<>();
            rows.add(new String[]{"0", "SELECT STATEMENT", "", String.valueOf(root.estimatedRows()), fmtCost(root.cost())});
            int[] counter = {1};
            walk(root, 1, rows, counter, predicates);
            String sep = "-".repeat(96);
            StringBuilder sb = new StringBuilder();
            sb.append("Plan hash value: ").append(Math.abs(stmt.selectList().hashCode() % 4_000_000_000L)).append("\n\n").append(sep).append('\n');
            sb.append(String.format("| %-3s | %-40s | %-24s | %6s | %11s |%n", "Id", "Operation", "Name", "Rows", "Cost (%CPU)"));
            sb.append(sep).append('\n');
            for (String[] r : rows) {
                boolean hasPred = r[0].startsWith("*");
                sb.append(String.format("|%s%-3s | %-40s | %-24s | %6s | %11s |%n", hasPred ? "*" : " ", r[0].replace("*", ""), r[1], r[2], r[3], r[4]));
            }
            sb.append(sep).append("\n\nPredicate Information (identified by operation id):\n---------------------------------------------------\n");
            predicates.forEach(p -> sb.append("   ").append(p).append('\n'));
            sb.append(String.format(Locale.US, "%nElapsed: %.3f s%n", root.actualMs() / 1000));
            return sb.toString();
        }

        private void walk(PlanNode n, int depth, List<String[]> rows, int[] counter, List<String> preds) {
            String pad = " ".repeat(depth);
            String name = n.target() == null ? "" : n.target().toUpperCase();
            List<String[]> extra = new ArrayList<>();
            String op;
            switch (n.operation()) {
                case "Seq Scan" -> op = "TABLE ACCESS FULL";
                case "Index Scan", "Index Scan Backward" -> {
                    boolean unique = n.details().stream().anyMatch(d -> d.startsWith("Index Cond")) && n.estimatedRows() == 1;
                    op = "TABLE ACCESS BY INDEX ROWID BATCHED";
                    extra.add(new String[]{"", pad + " " + (unique ? "INDEX UNIQUE SCAN" : "INDEX RANGE SCAN" + (n.operation().endsWith("Backward") ? " DESCENDING" : "")), n.indexName().toUpperCase()});
                }
                case "Sort" -> op = "SORT ORDER BY";
                case "Limit" -> op = "COUNT STOPKEY";
                case "Aggregate" -> op = "SORT AGGREGATE";
                case "Nested Loop" -> op = "NESTED LOOPS";
                case "Hash Join" -> op = "HASH JOIN";
                case "Hash" -> op = null;
                default -> op = n.operation().toUpperCase();
            }
            if (op != null) {
                int id = counter[0]++;
                boolean hasPred = n.details().stream().anyMatch(d -> d.startsWith("Filter") || d.startsWith("Index Cond") || d.startsWith("Hash Cond"));
                rows.add(new String[]{(hasPred ? "*" : "") + id, pad + op, name, String.valueOf(n.estimatedRows()), fmtCost(n.cost())});
                n.details().stream().filter(d -> d.startsWith("Filter")).forEach(d -> preds.add(id + " - filter" + d.substring("Filter:".length())));
                n.details().stream().filter(d -> d.startsWith("Hash Cond")).forEach(d -> preds.add(id + " - access" + d.substring("Hash Cond:".length())));
                for (String[] e : extra) {
                    int eid = counter[0]++;
                    rows.add(new String[]{"*" + eid, e[1], e[2], String.valueOf(n.estimatedRows()), fmtCost(n.cost() / 3)});
                    n.details().stream().filter(d -> d.startsWith("Index Cond")).forEach(d -> preds.add(eid + " - access" + d.substring("Index Cond:".length())));
                }
            }
            for (PlanNode c : n.children()) walk(c, op == null ? depth : depth + 1, rows, counter, preds);
        }

        private static String fmtCost(double cost) {
            long c = Math.round(cost);
            String v = c >= 1_000_000 ? (c / 1_000_000) + "M" : c >= 10_000 ? (c / 1000) + "K" : String.valueOf(c);
            return v + " (1)";
        }
    }
}
