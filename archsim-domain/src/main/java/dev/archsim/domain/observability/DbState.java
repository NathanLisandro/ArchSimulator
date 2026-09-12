package dev.archsim.domain.observability;

import dev.archsim.domain.company.DatabaseSchema;

import java.util.List;

/** Snapshot of the database as the SQL console sees it. effectiveSchema may differ from the company's (e.g. an index that was dropped). */
public record DbState(List<ActiveSession> sessions, List<LockInfo> locks, List<StatementStat> statements, List<TableStat> tableStats,
                      DatabaseSchema effectiveSchema) {}
