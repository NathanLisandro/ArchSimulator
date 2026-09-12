package dev.archsim.infrastructure.sandbox;

import dev.archsim.application.dto.SqlResult;
import dev.archsim.application.port.SqlSandbox;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.company.Column;
import dev.archsim.domain.company.Index;
import dev.archsim.domain.company.Table;
import dev.archsim.domain.observability.*;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.sql.SqlDialect;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * One in-memory H2 database (PostgreSQL mode) per case: the scenario tables with a small deterministic sample plus
 * catalog tables (pg_stat_statements, pg_stat_activity, pg_locks, pg_stat_user_tables, pg_indexes, table_sizes; v$ views
 * for Oracle) populated from the DbState. Queries run as a SELECT-only user with row and time limits.
 */
public class H2SqlSandbox implements SqlSandbox {
    static final int SAMPLE_ROWS = 300;
    static final int MAX_ROWS = 500;
    private static final int MAX_DATABASES = 64;

    private final Map<String, Boolean> built = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            if (size() <= MAX_DATABASES) return false;
            drop(eldest.getKey());
            return true;
        }
    };

    @Override
    public synchronized SqlResult execute(CaseId caseId, DbState db, SqlDialect dialect, String sql) {
        String name = dbName(caseId);
        if (!built.containsKey(name)) { build(name, db); built.put(name, Boolean.TRUE); } else built.get(name);
        String rewritten = dialect == SqlDialect.ORACLE ? sql.replaceAll("(?i)\\bv\\$", "v_").replaceAll("(?i)\\bgv\\$", "v_") : sql;
        long start = System.nanoTime();
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:" + name, "reader", "reader"); Statement st = c.createStatement()) {
            st.setMaxRows(MAX_ROWS + 1);
            boolean hasResult = st.execute(rewritten);
            if (!hasResult) return SqlResult.error("Console somente leitura: a instrução não retornou resultado.");
            try (ResultSet rs = st.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i).toLowerCase(Locale.ROOT));
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() < MAX_ROWS) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= cols.size(); i++) row.add(jsonSafe(rs.getObject(i)));
                    rows.add(row);
                }
                boolean truncated = rs.next();
                return new SqlResult(cols, rows, null, null, (System.nanoTime() - start) / 1_000_000, truncated);
            }
        } catch (SQLException e) {
            return SqlResult.error(translate(e, dialect));
        }
    }

    private static Object jsonSafe(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toInstant().toString();
        if (v instanceof java.sql.Date d) return d.toString();
        if (v instanceof java.math.BigDecimal bd) return bd.scale() <= 0 ? bd.longValue() : bd.doubleValue();
        if (v instanceof Number || v instanceof Boolean || v instanceof String) return v;
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant().toString();
        return v.toString();
    }

    private static String translate(SQLException e, SqlDialect dialect) {
        String m = e.getMessage() == null ? "erro" : e.getMessage();
        if (m.contains("Not enough rights")) return "ERROR: permission denied — console somente leitura";
        if (m.contains("Table") && m.contains("not found")) {
            String t = m.replaceAll("(?s).*Table \"?([A-Za-z0-9_$]+)\"? not found.*", "$1").toLowerCase(Locale.ROOT);
            return dialect == SqlDialect.ORACLE ? "ORA-00942: table or view does not exist (" + t.toUpperCase() + ")" : "ERROR:  relation \"" + t + "\" does not exist";
        }
        if (m.contains("Column") && m.contains("not found")) {
            String col = m.replaceAll("(?s).*Column \"?([A-Za-z0-9_.]+)\"? not found.*", "$1").toLowerCase(Locale.ROOT);
            return "ERROR:  column \"" + col + "\" does not exist";
        }
        if (m.contains("Timeout")) return "ERROR:  canceling statement due to statement timeout";
        return "ERROR:  " + m.replaceAll("(?s)\\s*\\[\\d+-\\d+]$", "").split("\n")[0];
    }

    private static String dbName(CaseId id) { return "case_" + id.value().replace("-", ""); }
    private static String url(String name) { return "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"; }

    private void drop(String name) {
        try (Connection c = DriverManager.getConnection(url(name), "sa", ""); Statement st = c.createStatement()) { st.execute("SHUTDOWN"); }
        catch (SQLException ignored) { }
    }

    private void build(String name, DbState db) {
        try (Connection c = DriverManager.getConnection(url(name), "sa", ""); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables WHERE lower(table_name) = 'pg_stat_statements'")) {
                if (rs.next() && rs.getLong(1) > 0) return; // already built in this JVM (in-memory DB outlives this instance)
            }
            SeededRandom random = new SeededRandom(name.hashCode());
            List<String> tables = new ArrayList<>();
            for (Table t : db.effectiveSchema().tables()) {
                st.execute(createTable(t));
                insertSample(c, t, random);
                tables.add(t.name());
            }
            tables.addAll(createCatalog(st, db));
            st.execute("CREATE USER IF NOT EXISTS reader PASSWORD 'reader'");
            for (String t : tables) st.execute("GRANT SELECT ON " + t + " TO reader");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot build sandbox " + name, e);
        }
    }

    static String createTable(Table t) {
        StringJoiner cols = new StringJoiner(", ");
        for (Column col : t.columns()) cols.add(col.name() + " " + h2Type(col.type()));
        return "CREATE TABLE " + t.name() + " (" + cols + ")";
    }

    static String h2Type(String type) {
        String l = type.toLowerCase(Locale.ROOT);
        if (l.startsWith("varchar2")) return "VARCHAR" + l.substring(8).replaceAll("\\s*(byte|char)", "");
        if (l.startsWith("varchar") || l.startsWith("char")) return l.toUpperCase();
        if (l.equals("text") || l.equals("jsonb") || l.equals("json") || l.equals("clob") || l.equals("uuid")) return "VARCHAR";
        if (l.startsWith("timestamp")) return "TIMESTAMP";
        if (l.equals("date")) return "DATE";
        if (l.startsWith("number") || l.startsWith("numeric") || l.startsWith("decimal")) return "DECIMAL(18,4)";
        if (l.equals("bigint") || l.equals("int8")) return "BIGINT";
        if (l.equals("integer") || l.equals("int") || l.equals("int4") || l.equals("smallint")) return "INT";
        if (l.equals("boolean") || l.equals("bool")) return "BOOLEAN";
        if (l.startsWith("double") || l.equals("real") || l.equals("float")) return "DOUBLE";
        return "VARCHAR";
    }

    private static void insertSample(Connection c, Table t, SeededRandom random) throws SQLException {
        int n = (int) Math.min(SAMPLE_ROWS, t.rowCount());
        String placeholders = String.join(", ", Collections.nCopies(t.columns().size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t.name() + " VALUES (" + placeholders + ")")) {
            for (int i = 1; i <= n; i++) {
                for (int j = 0; j < t.columns().size(); j++) ps.setObject(j + 1, sampleValue(t.columns().get(j), i, random));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Object sampleValue(Column col, int rowIndex, SeededRandom random) {
        String name = col.name().toLowerCase(Locale.ROOT);
        String type = h2Type(col.type());
        if (col.nullable() && random.nextDouble() < 0.15) return null;
        if (name.equals("id")) return (long) rowIndex * 1000 + 17;
        if (name.endsWith("_id")) return (long) random.nextInt(1, (int) Math.min(Integer.MAX_VALUE / 2, Math.max(2, col.distinctValues())));
        switch (type) {
            case "BIGINT": return name.contains("cents") ? (long) random.nextInt(100, 5_000_000) : (long) random.nextInt(1, 1_000_000);
            case "INT": return random.nextInt(1, 100_000);
            case "DECIMAL(18,4)": return java.math.BigDecimal.valueOf(random.nextInt(0, 10_000), name.equals("score") ? 4 : 2);
            case "DOUBLE": return random.nextDouble() * 1000;
            case "BOOLEAN": return random.nextDouble() < 0.5;
            case "TIMESTAMP": return Timestamp.from(Instant.parse("2026-09-12T00:00:00Z").minusSeconds(random.nextInt(0, 60 * 86400)));
            case "DATE": return java.sql.Date.valueOf(java.time.LocalDate.of(2026, 9, 12).minusDays(random.nextInt(0, 90)));
            default: break;
        }
        if (name.contains("document") || name.equals("cpf")) return String.format("%011d", random.nextLong(10_000_000_000L, 99_999_999_999L));
        if (name.contains("status")) return random.pick(List.of("PENDING", "SETTLED", "FAILED", "CANCELLED"));
        if (name.contains("email")) return "user" + rowIndex + "@example.com";
        if (name.contains("name")) return "Cliente " + rowIndex;
        if (name.contains("direction")) return random.pick(List.of("C", "D"));
        if (name.contains("type") || name.contains("arrangement")) return random.pick(List.of("PIX", "CARD", "TED", "FEE", "BOLETO"));
        if (name.contains("end_to_end") || name.contains("reference") || name.contains("idempotency") || name.contains("ref")) return "E" + String.format("%08d", 39_000_000 + rowIndex) + "2026091200" + String.format("%012d", random.nextLong(0, 999_999_999_999L));
        if (name.contains("rules") || name.contains("json")) return "{\"velocity\": " + random.nextInt(0, 9) + "}";
        if (name.contains("description")) return random.pick(List.of("PIX recebido", "Compra no cartão", "Transferência", "Tarifa", "Estorno"));
        return "v" + rowIndex;
    }

    private static List<String> createCatalog(Statement st, DbState db) throws SQLException {
        List<String> names = new ArrayList<>();
        st.execute("CREATE TABLE pg_stat_statements (queryid BIGINT, query VARCHAR, calls BIGINT, total_exec_time DOUBLE, mean_exec_time DOUBLE, max_exec_time DOUBLE, rows BIGINT, shared_blks_hit BIGINT, shared_blks_read BIGINT)");
        names.add("pg_stat_statements");
        long qid = 100_000;
        for (StatementStat s : db.statements()) {
            insert(st, "pg_stat_statements", qid++, s.query(), s.calls(), s.totalMs(), s.meanMs(), s.maxMs(), s.rows(), s.sharedBlksHit(), s.sharedBlksRead());
        }
        st.execute("CREATE TABLE pg_stat_activity (pid INT, datname VARCHAR, usename VARCHAR, application_name VARCHAR, client_addr VARCHAR, backend_start TIMESTAMP, query_start TIMESTAMP, state VARCHAR, wait_event_type VARCHAR, wait_event VARCHAR, query VARCHAR)");
        names.add("pg_stat_activity");
        for (ActiveSession s : db.sessions()) {
            insert(st, "pg_stat_activity", s.pid(), "archsim", s.user(), s.application(), s.clientAddr(), Timestamp.from(s.queryStart().minusSeconds(3600)), Timestamp.from(s.queryStart()), s.state(), s.waitEventType(), s.waitEvent(), s.query());
        }
        st.execute("CREATE TABLE pg_locks (pid INT, locktype VARCHAR, relation VARCHAR, mode VARCHAR, granted BOOLEAN, waiting_ms BIGINT)");
        names.add("pg_locks");
        for (LockInfo l : db.locks()) insert(st, "pg_locks", l.pid(), l.lockType(), l.relation(), l.mode(), l.granted(), l.waitingMs());
        st.execute("CREATE TABLE pg_stat_user_tables (relname VARCHAR, seq_scan BIGINT, seq_tup_read BIGINT, idx_scan BIGINT, n_live_tup BIGINT, n_dead_tup BIGINT, last_autovacuum TIMESTAMP)");
        names.add("pg_stat_user_tables");
        for (TableStat t : db.tableStats()) insert(st, "pg_stat_user_tables", t.table(), t.seqScan(), t.seqTupRead(), t.idxScan(), t.liveRows(), t.deadRows(), Timestamp.from(t.lastAutovacuum()));
        st.execute("CREATE TABLE pg_indexes (schemaname VARCHAR, tablename VARCHAR, indexname VARCHAR, indexdef VARCHAR)");
        st.execute("CREATE TABLE pg_stat_user_indexes (relname VARCHAR, indexrelname VARCHAR, idx_scan BIGINT, idx_tup_read BIGINT, size_mb BIGINT)");
        st.execute("CREATE TABLE table_sizes (table_name VARCHAR, row_estimate BIGINT, table_size_mb BIGINT, index_size_mb BIGINT, total_size_mb BIGINT)");
        names.addAll(List.of("pg_indexes", "pg_stat_user_indexes", "table_sizes"));
        SeededRandom r = new SeededRandom(7);
        for (Table t : db.effectiveSchema().tables()) {
            long idxSize = 0;
            for (Index i : t.indexes()) {
                idxSize += i.sizeMb();
                insert(st, "pg_indexes", "public", t.name(), i.name(), "CREATE " + (i.unique() ? "UNIQUE " : "") + "INDEX " + i.name() + " ON public." + t.name() + " USING " + i.type() + " (" + String.join(", ", i.columns()) + ")");
                insert(st, "pg_stat_user_indexes", t.name(), i.name(), r.nextLong(1_000, 900_000_000), r.nextLong(10_000, 9_000_000_000L), i.sizeMb());
            }
            insert(st, "table_sizes", t.name(), t.rowCount(), t.sizeMb(), idxSize, t.sizeMb() + idxSize);
        }
        // Oracle-flavoured views over the same data
        st.execute("CREATE VIEW v_session AS SELECT pid AS sid, pid + 10000 AS serial_no, usename AS username, application_name AS program, client_addr AS machine, state AS status, wait_event AS event, query_start AS sql_exec_start, query AS sql_text FROM pg_stat_activity");
        st.execute("CREATE VIEW v_sql AS SELECT queryid AS sql_id, query AS sql_text, calls AS executions, total_exec_time * 1000 AS elapsed_time, mean_exec_time * 1000 AS avg_elapsed_us, shared_blks_read AS disk_reads, shared_blks_hit AS buffer_gets, rows AS rows_processed FROM pg_stat_statements");
        st.execute("CREATE VIEW dba_tables AS SELECT UPPER(table_name) AS table_name, row_estimate AS num_rows, total_size_mb * 128 AS blocks FROM table_sizes");
        st.execute("CREATE VIEW dba_indexes AS SELECT UPPER(indexname) AS index_name, UPPER(tablename) AS table_name, 'NORMAL' AS index_type, 'VALID' AS status FROM pg_indexes");
        names.addAll(List.of("v_session", "v_sql", "dba_tables", "dba_indexes"));
        return names;
    }

    private static void insert(Statement st, String table, Object... values) throws SQLException {
        StringJoiner sj = new StringJoiner(", ");
        for (Object v : values) sj.add(literal(v));
        st.execute("INSERT INTO " + table + " VALUES (" + sj + ")");
    }

    private static String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Timestamp ts) return "TIMESTAMP '" + ts.toString() + "'";
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
