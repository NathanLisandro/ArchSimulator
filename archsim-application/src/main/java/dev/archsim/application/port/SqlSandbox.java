package dev.archsim.application.port;

import dev.archsim.application.dto.SqlResult;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.observability.DbState;
import dev.archsim.domain.sql.SqlDialect;

/** Executes read-only SQL against an in-memory replica of the scenario schema + catalog views (pg_stat_*, v$...). */
public interface SqlSandbox {
    SqlResult execute(CaseId caseId, DbState dbState, SqlDialect dialect, String sql);
}
