package dev.archsim.domain.sql;

import java.util.ArrayList;
import java.util.List;

public record ExplainPlan(PlanNode root, String text, double estimatedExecutionMs, boolean error) {
    public static ExplainPlan error(String message) { return new ExplainPlan(null, "ERROR:  " + message, 0, true); }

    /** All nodes, depth-first. */
    public List<PlanNode> nodes() {
        List<PlanNode> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(PlanNode n, List<PlanNode> out) {
        if (n == null) return;
        out.add(n);
        n.children().forEach(c -> collect(c, out));
    }

    public boolean hasSeqScanOn(String table) {
        return nodes().stream().anyMatch(n -> n.isSeqScan() && table.equalsIgnoreCase(n.tableName()));
    }
}
