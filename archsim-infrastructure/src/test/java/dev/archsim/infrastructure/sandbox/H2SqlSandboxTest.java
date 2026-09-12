package dev.archsim.infrastructure.sandbox;

import dev.archsim.application.dto.SqlResult;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.company.*;
import dev.archsim.domain.observability.*;
import dev.archsim.domain.sql.SqlDialect;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class H2SqlSandboxTest {
    private final Table ledger = new Table("ledger_entries", 80_000_000L, 42_000L,
            List.of(new Column("id", "bigint", 80_000_000L, false), new Column("account_id", "bigint", 2_000_000L, false),
                    new Column("created_at", "timestamp", 60_000_000L, false), new Column("amount_cents", "bigint", 500_000L, false),
                    new Column("status", "varchar(16)", 4L, false), new Column("rules_fired", "jsonb", 100L, true)),
            List.of(new Index("ledger_entries_pkey", List.of("id"), true, "btree", 1_700L)));
    private final DatabaseSchema schema = new DatabaseSchema(SqlDialect.POSTGRES, List.of(ledger));
    private final Instant now = Instant.parse("2026-09-12T15:00:00Z");
    private final DbState db = new DbState(
            List.of(new ActiveSession(4101, "app_account", "account-api", "10.0.1.5", "active", "IO", "DataFileRead", "SELECT * FROM ledger_entries WHERE account_id = 1", now, 3200)),
            List.of(new LockInfo(4101, "ledger_entries", "relation", "AccessShareLock", true, 0)),
            List.of(new StatementStat("SELECT * FROM ledger_entries WHERE account_id = $1", 5000, 20_000_000, 4000, 6000, 250_000, 1_000_000, 50_000),
                    new StatementStat("COMMIT", 900_000, 9000, 0.01, 1, 0, 0, 0)),
            List.of(new TableStat("ledger_entries", 5200, 400_000_000_000L, 90_000_000, 80_000_000, 500_000, 42_000, now)), schema);
    private final H2SqlSandbox sandbox = new H2SqlSandbox();
    private final CaseId caseId = CaseId.of("11111111-2222-3333-4444-555555555555");

    @Test
    void queriesCatalogTablesPopulatedFromDbState() {
        SqlResult r = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "SELECT query, calls, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 5");
        assertThat(r.error()).isNull();
        assertThat(r.columns()).containsExactly("query", "calls", "mean_exec_time");
        assertThat(r.rows().getFirst().getFirst()).isEqualTo("SELECT * FROM ledger_entries WHERE account_id = $1");
        assertThat(sandbox.execute(caseId, db, SqlDialect.POSTGRES, "select pid, state, wait_event, query from pg_stat_activity where state = 'active'").rows()).hasSize(1);
        assertThat(sandbox.execute(caseId, db, SqlDialect.POSTGRES, "select * from pg_indexes where tablename = 'ledger_entries'").rows()).hasSize(1);
        assertThat(sandbox.execute(caseId, db, SqlDialect.POSTGRES, "select row_estimate from table_sizes").rows().getFirst().getFirst()).isEqualTo(80_000_000L);
    }

    @Test
    void queriesScenarioTablesWithASampleAndIsReadOnly() {
        SqlResult r = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "SELECT id, status, created_at FROM ledger_entries WHERE amount_cents > 0 ORDER BY id LIMIT 3");
        assertThat(r.error()).isNull();
        assertThat(r.rows()).hasSize(3);
        assertThat(r.rows().getFirst().get(2)).asString().startsWith("2026-");
        SqlResult count = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "select count(*) from ledger_entries");
        assertThat(count.rows().getFirst().getFirst()).isEqualTo((long) H2SqlSandbox.SAMPLE_ROWS);

        SqlResult write = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "DELETE FROM ledger_entries");
        assertThat(write.error()).containsIgnoringCase("somente leitura");
        SqlResult ddl = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "CREATE INDEX x ON ledger_entries(account_id)");
        assertThat(ddl.error()).isNotNull();
        SqlResult missing = sandbox.execute(caseId, db, SqlDialect.POSTGRES, "select * from nope");
        assertThat(missing.error()).contains("relation \"nope\" does not exist");
    }

    @Test
    void oracleDialectRewritesDollarViews() {
        SqlResult r = sandbox.execute(caseId, db, SqlDialect.ORACLE, "SELECT sid, status, sql_text FROM v$session WHERE status = 'active'");
        assertThat(r.error()).isNull();
        assertThat(r.rows()).hasSize(1);
        assertThat(sandbox.execute(caseId, db, SqlDialect.ORACLE, "select * from dba_tables").rows().getFirst().getFirst()).isEqualTo("LEDGER_ENTRIES");
    }
}
