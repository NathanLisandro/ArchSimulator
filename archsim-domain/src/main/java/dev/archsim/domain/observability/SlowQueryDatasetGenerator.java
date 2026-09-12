package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.ScenarioSpec;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.DatabaseSchema;
import dev.archsim.domain.company.Table;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scenario "slow-query": one endpoint of one service degrades at sinceAt because one statement started doing a
 * sequential scan (missing/dropped index, non-sargable predicate, implicit cast...). Params: service, endpoint,
 * table, slowSql, p99Before, p99After, sinceAt, optional dropIndex, optional datastore.
 */
public final class SlowQueryDatasetGenerator implements ScenarioDatasetGenerator {
    @Override public String kind() { return "slow-query"; }

    @Override
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        ScenarioSpec sc = theCase.scenario();
        Instant now = theCase.generatedAt();
        Instant since = Instant.parse(sc.param("sinceAt"));
        String service = sc.param("service");
        String table = sc.param("table");
        double p99Before = Double.parseDouble(sc.param("p99Before"));
        double p99After = Double.parseDouble(sc.param("p99After"));
        String datastore = sc.paramOr("datastore", company.architecture().datastores().isEmpty() ? "core-db" : company.architecture().datastores().getFirst().name());

        TimeWindow window = new TimeWindow(now.minus(Duration.ofHours(24)), now);
        SeriesBuilder builder = new SeriesBuilder(window, Duration.ofMinutes(1), random.fork("noise"));
        BaselineMetrics baseline = new BaselineMetrics(builder, random.fork("baseline"));

        List<MetricSeries> metrics = new ArrayList<>();
        for (MetricSeries s : baseline.forCompany(company)) {
            if (!s.service().equals(service) && !s.service().equals(datastore)) { metrics.add(s); continue; }
            metrics.add(override(s, builder, service, datastore, since, p99Before, p99After));
        }

        DatabaseSchema effective = company.schema();
        String dropIndex = sc.paramOr("dropIndex", null);
        if (dropIndex != null) {
            Table t = effective.table(table).orElseThrow(() -> new IllegalArgumentException("Unknown table " + table));
            effective = effective.withTable(t.withoutIndex(dropIndex));
        }
        DbState db = dbState(sc, company, effective, random.fork("db"), now, since, p99After);
        return new ObservabilityDataset(window, List.copyOf(metrics), List.of(), List.of(), List.of(), List.of(),
                List.of(new Alert(since.plus(Duration.ofMinutes(4)), "HighLatencyP99", service, "warning",
                        sc.param("endpoint") + " p99 > " + Math.round(p99Before * 5) + "ms por 3 minutos", null)),
                List.of(), db);
    }

    private MetricSeries override(MetricSeries s, SeriesBuilder b, String service, String datastore, Instant since, double before, double after) {
        Instant rampEnd = since.plus(Duration.ofMinutes(8));
        if (s.service().equals(service)) {
            return switch (s.metric()) {
                case "latency_p99" -> b.series(service, s.metric(), s.unit(), Shape.ramp(since, rampEnd, before, after), after * 0.06);
                case "latency_p95" -> b.series(service, s.metric(), s.unit(), Shape.ramp(since, rampEnd, before * 0.6, after * 0.7), after * 0.05);
                case "latency_p50" -> b.series(service, s.metric(), s.unit(), Shape.ramp(since, rampEnd, before * 0.3, before * 0.9), before * 0.1);
                case "db_pool_in_use" -> b.series(service, s.metric(), s.unit(), Shape.daily(6, 3, 15).plus(Shape.ramp(since, rampEnd, 0, 11)).clamp(0, 20), 1.5);
                case "error_rate" -> b.series(service, s.metric(), s.unit(), Shape.constant(0.2).plus(Shape.ramp(since, rampEnd, 0, 1.4)), 0.3);
                default -> s;
            };
        }
        return switch (s.metric()) {
            case "cpu_utilization" -> b.series(datastore, s.metric(), s.unit(), Shape.daily(30, 12, 15).plus(Shape.ramp(since, rampEnd, 0, 38)).clamp(0, 100), 5);
            case "read_iops" -> b.series(datastore, s.metric(), s.unit(), Shape.daily(1800, 900, 15).plus(Shape.ramp(since, rampEnd, 0, 6500)), 350);
            case "read_latency" -> b.series(datastore, s.metric(), s.unit(), Shape.constant(1.8).plus(Shape.ramp(since, rampEnd, 0, 4.5)), 0.6);
            case "cache_hit_ratio" -> b.series(datastore, s.metric(), s.unit(), Shape.constant(98.5).plus(Shape.ramp(since, rampEnd, 0, -9)), 0.8);
            default -> s;
        };
    }

    private DbState dbState(ScenarioSpec sc, Company company, DatabaseSchema effective, RandomSource random, Instant now, Instant since, double p99After) {
        String slowSql = sc.param("slowSql");
        String table = sc.param("table");
        String app = sc.param("service");
        long minutesSince = Math.max(1, Duration.between(since, now).toMinutes());
        long calls = minutesSince * random.nextInt(40, 120);
        double mean = p99After * (0.85 + random.nextDouble() * 0.1);

        List<StatementStat> statements = new ArrayList<>();
        statements.add(new StatementStat(slowSql, calls, calls * mean, mean, mean * 1.6, calls * 50L, calls * 5200L, calls * 900L));
        for (Table t : effective.tables()) {
            String pk = t.columns().isEmpty() ? "id" : t.columns().getFirst().name();
            String other = t.columns().size() > 1 ? t.columns().get(1).name() : pk;
            long c = random.nextLong(20_000, 2_000_000);
            double m = 0.2 + random.nextDouble() * 3;
            statements.add(new StatementStat("SELECT * FROM " + t.name() + " WHERE " + pk + " = $1", c, c * m, m, m * 8, c, c / 3, c * 4));
            long c2 = random.nextLong(5_000, 400_000);
            double m2 = 1 + random.nextDouble() * 12;
            statements.add(new StatementStat("UPDATE " + t.name() + " SET updated_at = now() WHERE " + pk + " = $1", c2, c2 * m2, m2, m2 * 6, c2, c2 / 5, c2 * 3));
            if (random.nextDouble() < 0.7) {
                long c3 = random.nextLong(1_000, 90_000);
                double m3 = 2 + random.nextDouble() * 25;
                statements.add(new StatementStat("SELECT count(*) FROM " + t.name() + " WHERE " + other + " = $1 AND created_at >= $2", c3, c3 * m3, m3, m3 * 4, c3, c3 * 40, c3 * 200));
            }
            long c4 = random.nextLong(10_000, 800_000);
            statements.add(new StatementStat("INSERT INTO " + t.name() + " VALUES ($1, $2, $3, $4)", c4, c4 * 0.4, 0.4, 9.2, c4, 0, c4 * 2));
        }
        statements.add(new StatementStat("COMMIT", random.nextLong(500_000, 9_000_000), 90_000, 0.05, 3.1, 0, 0, 0));
        statements.add(new StatementStat("SELECT 1", random.nextLong(200_000, 4_000_000), 8_000, 0.01, 0.9, 0, 0, 0));
        statements.sort((a, b) -> Double.compare(b.meanMs(), a.meanMs()));

        List<ActiveSession> sessions = new ArrayList<>();
        int pid = 41000 + random.nextInt(0, 999);
        int active = random.nextInt(3, 6);
        for (int i = 0; i < active; i++) {
            long dur = Math.round(mean * (0.2 + random.nextDouble() * 0.9));
            sessions.add(new ActiveSession(pid + i, "app_" + app.replace('-', '_'), app, "10.0." + random.nextInt(1, 4) + "." + random.nextInt(10, 250),
                    "active", "IO", "DataFileRead", slowSql.replace("$1", String.valueOf(random.nextInt(10_000, 999_999))).replace("$2", "'" + now.minus(Duration.ofDays(30)).toString().substring(0, 10) + "'"),
                    now.minusMillis(dur), dur));
        }
        for (int i = 0; i < random.nextInt(4, 9); i++) {
            String svc = company.architecture().services().isEmpty() ? app : random.pick(company.architecture().services()).name();
            sessions.add(new ActiveSession(pid + 100 + i, "app_" + svc.replace('-', '_'), svc, "10.0." + random.nextInt(1, 4) + "." + random.nextInt(10, 250),
                    random.nextDouble() < 0.7 ? "idle" : "idle in transaction", "Client", "ClientRead", "COMMIT", now.minusSeconds(random.nextInt(1, 120)), random.nextInt(1, 40)));
        }

        List<TableStat> tableStats = new ArrayList<>();
        for (Table t : effective.tables()) {
            boolean target = t.name().equalsIgnoreCase(table);
            long seq = target ? calls + random.nextLong(100, 900) : random.nextLong(0, 40);
            tableStats.add(new TableStat(t.name(), seq, seq * (target ? t.rowCount() : Math.min(t.rowCount(), 1000)), random.nextLong(100_000, 90_000_000),
                    t.rowCount(), t.rowCount() / random.nextInt(40, 400), t.sizeMb(), now.minus(Duration.ofHours(random.nextInt(2, 30)))));
        }
        return new DbState(List.copyOf(sessions), List.of(), List.copyOf(statements), List.copyOf(tableStats), effective);
    }
}
