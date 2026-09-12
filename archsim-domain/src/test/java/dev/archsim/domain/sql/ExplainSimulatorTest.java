package dev.archsim.domain.sql;

import dev.archsim.domain.company.Column;
import dev.archsim.domain.company.DatabaseSchema;
import dev.archsim.domain.company.Index;
import dev.archsim.domain.company.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainSimulatorTest {

    private static final Table LEDGER = new Table("ledger_entries", 80_000_000L, 42_000L,
            List.of(new Column("id", "bigint", 80_000_000L, false),
                    new Column("account_id", "bigint", 2_000_000L, false),
                    new Column("created_at", "timestamp", 60_000_000L, false),
                    new Column("amount_cents", "bigint", 500_000L, false),
                    new Column("status", "varchar(16)", 4L, false)),
            List.of(new Index("ledger_entries_pkey", List.of("id"), true, "btree", 1_700L),
                    new Index("idx_ledger_status", List.of("status"), false, "btree", 900L)));

    private static final Table ACCOUNTS = new Table("accounts", 2_000_000L, 800L,
            List.of(new Column("id", "bigint", 2_000_000L, false),
                    new Column("document", "varchar(14)", 2_000_000L, false)),
            List.of(new Index("accounts_pkey", List.of("id"), true, "btree", 45L),
                    new Index("idx_accounts_document", List.of("document"), true, "btree", 60L)));

    private final ExplainSimulator simulator = new ExplainSimulator();

    private DatabaseSchema schema(Table... tables) {
        return new DatabaseSchema(SqlDialect.POSTGRES, List.of(tables));
    }

    @Test
    void seqScanWhenNoUsableIndex() {
        ExplainPlan plan = simulator.explain(
                "SELECT * FROM ledger_entries e WHERE e.account_id = 88213 AND e.created_at >= '2026-08-01' ORDER BY e.created_at DESC LIMIT 50",
                schema(LEDGER), SqlDialect.POSTGRES);

        assertThat(plan.text()).contains("Seq Scan on ledger_entries e");
        assertThat(plan.text()).contains("Filter:");
        assertThat(plan.text()).contains("Sort Key: e.created_at DESC");
        assertThat(plan.text()).contains("Limit");
        assertThat(plan.estimatedExecutionMs()).isGreaterThan(2000);
        assertThat(plan.text()).containsPattern("Execution Time: \\d+\\.\\d+ ms");
    }

    @Test
    void indexScanWhenCompositeIndexMatchesEqualityAndRange() {
        Table indexed = LEDGER.withIndex(new Index("idx_ledger_account_created", List.of("account_id", "created_at"), false, "btree", 3_000L));
        ExplainPlan plan = simulator.explain(
                "SELECT * FROM ledger_entries e WHERE e.account_id = 88213 AND e.created_at >= '2026-08-01' ORDER BY e.created_at DESC LIMIT 50",
                schema(indexed), SqlDialect.POSTGRES);

        assertThat(plan.text()).contains("Index Scan Backward using idx_ledger_account_created on ledger_entries e");
        assertThat(plan.text()).contains("Index Cond:");
        assertThat(plan.text()).doesNotContain("Sort Key");
        assertThat(plan.estimatedExecutionMs()).isLessThan(50);
    }

    @Test
    void lowSelectivityIndexIsIgnoredInFavourOfSeqScan() {
        ExplainPlan plan = simulator.explain("SELECT count(*) FROM ledger_entries WHERE status = 'SETTLED'",
                schema(LEDGER), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Seq Scan on ledger_entries");
        assertThat(plan.text()).contains("Aggregate");
    }

    @Test
    void functionOnColumnDefeatsIndex() {
        ExplainPlan plan = simulator.explain("SELECT * FROM accounts WHERE lower(document) = '12345678900'",
                schema(ACCOUNTS), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Seq Scan on accounts");
        assertThat(plan.text()).contains("Filter: (lower(document)");
    }

    @Test
    void implicitCastDefeatsIndex() {
        ExplainPlan plan = simulator.explain("SELECT * FROM accounts WHERE document = 12345678900",
                schema(ACCOUNTS), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Seq Scan on accounts");
        assertThat(plan.text()).contains("::numeric");
    }

    @Test
    void uniqueIndexEqualityGivesIndexScanWithOneRow() {
        ExplainPlan plan = simulator.explain("SELECT * FROM accounts WHERE document = '12345678900'",
                schema(ACCOUNTS), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Index Scan using idx_accounts_document on accounts");
        assertThat(plan.text()).contains("rows=1 ");
    }

    @Test
    void joinUsesNestedLoopWhenInnerSideIsIndexed() {
        Table indexed = LEDGER.withIndex(new Index("idx_ledger_account_created", List.of("account_id", "created_at"), false, "btree", 3_000L));
        ExplainPlan plan = simulator.explain(
                "SELECT a.document, e.amount_cents FROM ledger_entries e JOIN accounts a ON a.id = e.account_id WHERE e.account_id = 88213",
                schema(indexed, ACCOUNTS), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Nested Loop");
        assertThat(plan.text()).contains("Index Scan using accounts_pkey on accounts a");
    }

    @Test
    void hashJoinWhenBothSidesAreLarge() {
        ExplainPlan plan = simulator.explain(
                "SELECT a.document, e.amount_cents FROM ledger_entries e JOIN accounts a ON a.id = e.account_id WHERE e.status = 'PENDING'",
                schema(LEDGER, ACCOUNTS), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("Hash Join");
        assertThat(plan.text()).contains("Hash Cond: (a.id = e.account_id)");
    }

    @Test
    void oracleFormatRendersTabularPlan() {
        ExplainPlan plan = simulator.explain(
                "SELECT * FROM ledger_entries e WHERE e.account_id = 88213 ORDER BY e.created_at DESC",
                new DatabaseSchema(SqlDialect.ORACLE, List.of(LEDGER)), SqlDialect.ORACLE);
        assertThat(plan.text()).contains("| Id  | Operation");
        assertThat(plan.text()).contains("TABLE ACCESS FULL");
        assertThat(plan.text()).contains("LEDGER_ENTRIES");
        assertThat(plan.text()).contains("SORT ORDER BY");
    }

    @Test
    void unknownTableIsReportedAsError() {
        ExplainPlan plan = simulator.explain("SELECT * FROM nope", schema(LEDGER), SqlDialect.POSTGRES);
        assertThat(plan.text()).contains("relation \"nope\" does not exist");
    }
}
