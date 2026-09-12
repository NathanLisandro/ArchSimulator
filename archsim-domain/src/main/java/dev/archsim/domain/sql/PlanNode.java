package dev.archsim.domain.sql;

import java.util.List;

public record PlanNode(String operation, String target, String indexName, List<String> details, long estimatedRows,
                       double cost, double actualMs, List<PlanNode> children) {
    public PlanNode withChildren(List<PlanNode> newChildren) {
        return new PlanNode(operation, target, indexName, details, estimatedRows, cost, actualMs, newChildren);
    }
    /** Appends " alias" to the target for display ("ledger_entries e"). */
    public PlanNode withAlias(String aliasSuffix) {
        if (aliasSuffix == null || aliasSuffix.isBlank()) return this;
        return new PlanNode(operation, target + aliasSuffix, indexName, details, estimatedRows, cost, actualMs, children);
    }
    public String tableName() { return target == null ? null : target.split(" ")[0]; }
    public boolean isSeqScan() { return operation.startsWith("Seq Scan"); }
    public boolean usesIndex() { return indexName != null; }
}
