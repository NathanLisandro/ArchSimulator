package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.ScenarioSpec;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.DatabaseSchema;
import dev.archsim.domain.company.ServiceComponent;
import dev.archsim.domain.company.Table;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Scenario "incident": paints one {@link IncidentRootCause} signature over healthy-looking telemetry.
 *
 * <p>Scenario params: rootCause, service, onsetAt; optional deployAt, deployService, deployVersion, datastore, broker,
 * topic, queue, cache, partner, table, index, p99Before, p99After.
 */
public final class IncidentDatasetGenerator implements ScenarioDatasetGenerator {
    private static final Duration WINDOW = Duration.ofHours(6);
    private static final Duration STEP = Duration.ofMinutes(1);
    private static final int BASELINE_LOG_LINES_PER_SERVICE = 1200;
    /** Quanto tempo a telemetria leva para voltar ao normal depois de uma mitigação que resolve. */
    private static final Duration RECOVERY = Duration.ofMinutes(4);

    @Override public String kind() { return "incident"; }

    @Override
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        return generate(theCase, company, random, null);
    }

    @Override
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random, Instant recoveredAt) {
        ScenarioSpec sc = theCase.scenario();
        IncidentRootCause cause = IncidentRootCause.of(sc.param("rootCause"));
        Instant now = theCase.generatedAt();
        Instant onset = Instant.parse(sc.param("onsetAt"));
        // O tempo anda enquanto o incidente é respondido: quando há mitigação, a janela avança para mostrar a recuperação.
        Instant end = recoveredAt == null ? now : latest(now, recoveredAt.plus(RECOVERY).plus(Duration.ofMinutes(6)));
        TimeWindow window = new TimeWindow(end.minus(WINDOW), end);
        String service = sc.param("service");
        String datastore = sc.paramOr("datastore", company.architecture().datastores().isEmpty() ? "core-db" : company.architecture().datastores().getFirst().name());
        double p99Before = Double.parseDouble(sc.paramOr("p99Before", "180"));
        double p99After = Double.parseDouble(sc.paramOr("p99After", "3200"));

        SeriesBuilder builder = new SeriesBuilder(window, STEP, random.fork("noise"));
        Map<String, MetricSeries> metrics = new LinkedHashMap<>();
        for (MetricSeries s : new BaselineMetrics(builder, random.fork("baseline")).forCompany(company)) metrics.put(s.key(), s);

        LogGenerator logs = new LogGenerator(window, random.fork("logs"));
        TraceGenerator traces = new TraceGenerator(random.fork("traces"));
        List<LogEntry> logLines = new ArrayList<>(logs.baseline(company, BASELINE_LOG_LINES_PER_SERVICE));
        List<Trace> traceList = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();

        Signature signature = new Signature(sc, cause, company, window, builder, metrics, logs, traces, logLines, traceList, alerts,
                random.fork("signature"), onset, service, datastore, p99Before, p99After, now, recoveredAt);
        signature.paint();

        // Healthy traces so the slow ones have to be found, not just read.
        for (int i = 0; i < 12; i++) {
            Instant at = window.from().plusSeconds(random.nextLong(0, window.duration().getSeconds()));
            traceList.add(traces.healthy(logs.traceId(), service, operationName(sc), at));
        }
        // Every incident trace has at least one log line carrying its id, so log ↔ trace correlation works.
        for (Trace t : traceList) {
            if (t.durationMs() < p99Before * 2) continue;
            logLines.add(new LogEntry(t.startedAt(), t.rootService(), "WARN", t.traceId(), t.durationMs(),
                    "Slow request completed in " + t.durationMs() + "ms", Map.of("pod", t.rootService() + "-1", "endpoint", t.rootOperation(), "env", "prod")));
        }
        logLines.sort(Comparator.comparing(LogEntry::timestamp));

        List<DeployEvent> deploys = deploys(sc, company, random.fork("deploys"), window, onset);
        List<FeatureFlagChange> flags = flags(sc, cause, random.fork("flags"), window, onset);
        List<InstanceStatus> instances = instances(company, service, cause, random.fork("instances"), window, now);
        DbState dbState = dbState(company, service, cause, sc, random.fork("db"), now, onset);

        return new ObservabilityDataset(window, List.copyOf(metrics.values()), List.copyOf(logLines), List.copyOf(traceList),
                deploys, flags, List.copyOf(alerts), instances, dbState);
    }

    // ---------------------------------------------------------------- signature

    /** Applies the recognisable pattern of one root cause across metrics, logs, traces and alerts. */
    private static final class Signature {
        private final ScenarioSpec sc;
        private final IncidentRootCause cause;
        private final Company company;
        private final TimeWindow window;
        private final SeriesBuilder builder;
        private final Map<String, MetricSeries> metrics;
        private final LogGenerator logs;
        private final TraceGenerator traces;
        private final List<LogEntry> logLines;
        private final List<Trace> traceList;
        private final List<Alert> alerts;
        private final RandomSource random;
        private final Instant onset;
        private final String service;
        private final String datastore;
        private final double p99Before;
        private final double p99After;
        private final Instant now;
        private final Instant rampEnd;
        /** Instante em que a mitigação do usuário resolveu o incidente; nulo enquanto ele segue aberto. */
        private final Instant recoveredAt;

        Signature(ScenarioSpec sc, IncidentRootCause cause, Company company, TimeWindow window, SeriesBuilder builder,
                  Map<String, MetricSeries> metrics, LogGenerator logs, TraceGenerator traces, List<LogEntry> logLines,
                  List<Trace> traceList, List<Alert> alerts, RandomSource random, Instant onset, String service,
                  String datastore, double p99Before, double p99After, Instant now, Instant recoveredAt) {
            this.sc = sc; this.cause = cause; this.company = company; this.window = window; this.builder = builder;
            this.metrics = metrics; this.logs = logs; this.traces = traces; this.logLines = logLines; this.traceList = traceList;
            this.alerts = alerts; this.random = random; this.onset = onset; this.service = service; this.datastore = datastore;
            this.p99Before = p99Before; this.p99After = p99After; this.now = now; this.recoveredAt = recoveredAt;
            this.rampEnd = onset.plus(Duration.ofMinutes(8));
        }

        void paint() {
            switch (cause) {
                case N_PLUS_ONE -> nPlusOne();
                case INDEX_REMOVED -> indexRemoved();
                case POOL_EXHAUSTION -> poolExhaustion();
                case KAFKA_HOT_PARTITION -> kafkaHotPartition();
                case RETRY_STORM -> retryStorm();
                case CACHE_STAMPEDE -> cacheStampede();
                case LONG_LOCK -> longLock();
                case MEMORY_LEAK -> memoryLeak();
                case THIRD_PARTY_TIMEOUT -> thirdPartyTimeout();
                case CLOCK_SKEW -> clockSkew();
                case DLQ_GROWING -> dlqGrowing();
                case FEATURE_FLAG -> featureFlag();
            }
        }

        // ---- individual signatures

        private void nPlusOne() {
            degradeLatency(service);
            put(datastore, "read_iops", "iops", Shape.daily(1800, 900, 15).plus(Shape.ramp(onset, rampEnd, 0, 14_000)), 400);
            put(datastore, "cpu_utilization", "%", Shape.daily(30, 12, 15).plus(Shape.ramp(onset, rampEnd, 0, 40)).clamp(0, 100), 5);
            put(service, "db_pool_in_use", "connections", Shape.daily(6, 3, 15).plus(Shape.ramp(onset, rampEnd, 0, 9)).clamp(0, 20), 1.5);
            errorLines(18, "ERROR", List.of(
                    "Request timeout after 5000ms calling " + datastore,
                    "org.springframework.dao.QueryTimeoutException: statement timeout"), Map.of("status", "504"));
            String table = sc.paramOr("table", "ledger_entries");
            for (int i = 0; i < 6; i++) {
                traceList.add(traces.nPlusOne(logs.traceId(), service, operationName(sc), table,
                        onset.plusSeconds(random.nextLong(60, Math.max(120, Duration.between(onset, now).getSeconds()))), random.nextInt(28, 90)));
            }
            alert("HighLatencyP99", service, "critical", "p99 acima de " + Math.round(p99Before * 5) + "ms por 5 minutos");
        }

        private void indexRemoved() {
            degradeLatency(service);
            put(datastore, "read_iops", "iops", Shape.daily(1800, 900, 15).plus(Shape.ramp(onset, rampEnd, 0, 9000)), 400);
            put(datastore, "cpu_utilization", "%", Shape.daily(30, 12, 15).plus(Shape.ramp(onset, rampEnd, 0, 45)).clamp(0, 100), 5);
            put(datastore, "cache_hit_ratio", "%", Shape.constant(98.5).plus(Shape.ramp(onset, rampEnd, 0, -11)), 0.8);
            errorLines(8, "WARN", List.of("Slow query detected (" + Math.round(p99After) + "ms)"), Map.of("status", "200"));
            dominatedTrace("SELECT FROM " + sc.paramOr("table", "ledger_entries"), Math.round(p99After * 0.9),
                    Map.of("db.system", "postgresql", "db.plan", "Seq Scan"));
            alert("HighLatencyP99", service, "critical", "p99 acima do SLO desde " + onset);
        }

        private void poolExhaustion() {
            // The ceiling is the pool the company declares; 20 keeps older scenarios unchanged. No noise: a gauge of
            // connections in use cannot read above the pool size, and a saturated pool shows as a flat line at the max.
            int pool = Integer.parseInt(sc.paramOr("poolSize", "20"));
            degradeLatency(service);
            put(service, "db_pool_in_use", "connections", Shape.daily(pool * 0.3, pool * 0.15, 15).plus(Shape.ramp(onset, rampEnd, 0, pool)).clamp(0, pool), 0);
            put(service, "db_pool_pending", "threads", Shape.ramp(onset, rampEnd, 0, 45), 4);
            put(service, "error_rate", "%", Shape.constant(0.2).plus(Shape.ramp(onset, rampEnd, 0, 12)), 0.5);
            put(datastore, "active_connections", "connections", Shape.daily(60, 25, 15).plus(Shape.ramp(onset, rampEnd, 0, 900)), 20);
            errorLines(26, "ERROR", List.of(
                    "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                    "Connection timeout acquiring connection from pool (waited 30000ms)"), Map.of("status", "503"));
            dominatedTrace("acquireConnection", 30_000, Map.of("db.pool", "HikariPool-1", "http.status_code", "503"));
            alert("ConnectionPoolSaturated", service, "critical", "pool em " + pool + "/" + pool + " conexões por mais de 3 minutos");
        }

        private void kafkaHotPartition() {
            String broker = sc.paramOr("broker", "kafka");
            String topic = sc.paramOr("topic", "events");
            put(broker, "consumer_lag", "messages", Shape.constant(220).plus(Shape.ramp(onset, now, 0, 1_400_000)), 400);
            put(broker, "bytes_in", "bytes/s", Shape.daily(4_000_000, 1_500_000, 15), 200_000);
            put(broker, "partition_7_lag", "messages", Shape.constant(60).plus(Shape.ramp(onset, now, 0, 1_380_000)), 300);
            put(broker, "partition_3_lag", "messages", Shape.constant(55), 30);
            put(service, "consumer_processing_time", "ms", Shape.constant(35).plus(Shape.ramp(onset, rampEnd, 0, 900)), 40);
            errorLines(12, "WARN", List.of(
                    "Consumer group lag growing on topic " + topic + " partition 7 (one key dominates)",
                    "Rebalance skipped: partition 7 still processing"), Map.of("topic", topic, "partition", "7"));
            alert("ConsumerLagHigh", broker, "warning", "lag do grupo do tópico " + topic + " acima de 1M mensagens");
        }

        private void retryStorm() {
            String partner = sc.paramOr("partner", "parceiro externo");
            degradeLatency(service);
            put(service, "requests_per_second", "rps", Shape.daily(200, 90, 15).plus(Shape.ramp(onset, rampEnd, 0, 700)), 40);
            put(service, "error_rate", "%", Shape.constant(0.2).plus(Shape.ramp(onset, rampEnd, 0, 28)), 1);
            put(service, "retry_count", "retries/s", Shape.constant(2).plus(Shape.ramp(onset, rampEnd, 0, 640)), 30);
            errorLines(30, "ERROR", List.of(
                    "Retrying call to " + partner + " (attempt 5 of 5)",
                    "Max retries exceeded calling " + partner + ": ReadTimeout"), Map.of("status", "502", "partner", partner));
            dominatedTrace("POST " + partner, Math.round(p99After * 0.85), Map.of("http.status_code", "504", "peer.service", partner));
            alert("ErrorRateHigh", service, "critical", "taxa de erro acima de 20% com retries em cascata");
        }

        private void cacheStampede() {
            String cache = sc.paramOr("cache", "redis-cache");
            degradeLatency(service);
            put(cache, "cache_hit_rate", "%", Shape.constant(96).plus(Shape.step(onset, 0, -74)), 1.5);
            put(cache, "current_connections", "connections", Shape.constant(400).plus(Shape.ramp(onset, rampEnd, 0, 2400)), 60);
            put(cache, "evictions", "keys/s", Shape.constant(1).plus(Shape.spike(onset, Duration.ofMinutes(4), 900)), 20);
            put(datastore, "read_iops", "iops", Shape.daily(1800, 900, 15).plus(Shape.spike(onset, Duration.ofMinutes(12), 12_000)), 400);
            errorLines(14, "WARN", List.of(
                    "Cache miss storm detected: 94% miss rate on key prefix balance:",
                    "Falling back to database for balance lookup"), Map.of("cache", cache));
            alert("CacheHitRateLow", cache, "warning", "hit ratio caiu de 96% para 22% em menos de 1 minuto");
        }

        private void longLock() {
            degradeLatency(service);
            put(datastore, "active_connections", "connections", Shape.daily(60, 25, 15).plus(Shape.ramp(onset, rampEnd, 0, 700)), 20);
            put(datastore, "read_iops", "iops", Shape.daily(1800, 900, 15), 300);
            put(datastore, "lock_waits", "waits", Shape.constant(0).plus(Shape.ramp(onset, rampEnd, 0, 380)), 15);
            errorLines(16, "ERROR", List.of(
                    "could not obtain lock on row in relation \"" + sc.paramOr("table", "ledger_entries") + "\"",
                    "org.postgresql.util.PSQLException: ERROR: canceling statement due to lock timeout"), Map.of("status", "500"));
            dominatedTrace("UPDATE " + sc.paramOr("table", "ledger_entries"), Math.round(p99After * 0.95),
                    Map.of("db.system", "postgresql", "db.wait_event", "Lock:transactionid"));
            alert("WriteLatencyHigh", datastore, "critical", "escritas bloqueadas por transação longa");
        }

        private void memoryLeak() {
            put(service, "memory_used", "%",
                    Shape.sawtooth(window.from(), Duration.ofMinutes(40), 52, 72).plus(Shape.ramp(window.from(), window.to(), 0, 26)).clamp(0, 100), 1.5);
            put(service, "gc_pause_p99", "ms", Shape.constant(18).plus(Shape.ramp(window.from(), window.to(), 0, 900)), 40);
            put(service, "latency_p99", "ms",
                    Shape.constant(p99Before).plus(Shape.ramp(onset, window.to(), 0, p99After - p99Before)), p99After * 0.05);
            errorLines(6, "WARN", List.of(
                    "GC pause of 1240ms (G1 Full GC)",
                    "java.lang.OutOfMemoryError: Java heap space (pod restarting)"), Map.of("status", "500"));
            alert("PodRestartLoop", service, "warning", "pods reiniciando por OOMKilled");
        }

        private void thirdPartyTimeout() {
            String partner = sc.paramOr("partner", "parceiro externo");
            degradeLatency(service);
            put(service, "error_rate", "%", Shape.constant(0.2).plus(Shape.ramp(onset, rampEnd, 0, 18)), 1);
            put(partner, "latency", "ms", Shape.constant(140).plus(Shape.ramp(onset, rampEnd, 0, 9800)), 200);
            put(partner, "timeouts", "count", Shape.constant(0).plus(Shape.ramp(onset, rampEnd, 0, 120)), 6);
            errorLines(20, "ERROR", List.of(
                    "java.net.SocketTimeoutException: Read timed out calling " + partner,
                    "DNS resolution took 5002ms for " + partner.toLowerCase().replace(' ', '-') + ".example.com"),
                    Map.of("status", "504", "partner", partner));
            dominatedTrace("POST " + partner, Math.round(p99After * 0.92), Map.of("peer.service", partner, "http.status_code", "504"));
            alert("DependencyTimeout", service, "critical", "timeouts na integração com " + partner);
        }

        private void clockSkew() {
            put(service, "duplicate_operations", "count", Shape.constant(0).plus(Shape.ramp(onset, now, 0, 320)), 12);
            put(service, "clock_offset", "ms", Shape.constant(12).plus(Shape.step(onset, 0, 41_000)), 300);
            errorLines(10, "WARN", List.of(
                    "Idempotency key collision window mismatch: pod clock ahead by 41s",
                    "Duplicate transaction detected for reference (idempotency key regenerated)"), Map.of("status", "200"));
            alert("DuplicateTransactions", service, "critical", "transações duplicadas detectadas na conciliação");
        }

        private void dlqGrowing() {
            String queue = sc.paramOr("queue", "notifications-dlq");
            put(service, "dlq_messages", "messages", Shape.constant(3).plus(Shape.ramp(onset, now, 0, 4200)), 20);
            put(service, "messages_processed", "msg/s", Shape.daily(120, 40, 15), 12);
            put(service, "age_of_oldest_message", "s", Shape.constant(2).plus(Shape.ramp(onset, now, 0, 5400)), 30);
            errorLines(9, "ERROR", List.of(
                    "Message moved to DLQ after 3 attempts: schema validation failed (field 'phone' missing)",
                    "Sending to " + queue + ": UnrecognizedPropertyException"), Map.of("queue", queue, "status", "500"));
            alert("DlqNotEmpty", service, "warning", "fila " + queue + " acumulando mensagens sem consumo");
        }

        private void featureFlag() {
            put(service, "latency_p99", "ms", Shape.constant(p99Before).plus(Shape.step(onset, 0, p99After - p99Before)), p99After * 0.08);
            put(service, "latency_p50", "ms", Shape.constant(p99Before * 0.3), p99Before * 0.1);
            put(service, "cpu_utilization", "%", Shape.daily(35, 15, 15).plus(Shape.step(onset, 0, 30)).clamp(0, 100), 6);
            errorLines(7, "WARN", List.of(
                    "Feature flag " + sc.paramOr("flag", "new-pricing-engine") + " enabled for 20% of traffic",
                    "Expensive path executed (recalculating full schedule)"), Map.of("status", "200"));
            dominatedTrace("recalculateSchedule", Math.round(p99After * 0.88), Map.of("feature.flag", sc.paramOr("flag", "new-pricing-engine")));
            alert("LatencyBimodal", service, "warning", "p99 subiu sem alteração no p50: parcela do tráfego em caminho caro");
        }

        // ---- helpers

        private void degradeLatency(String svc) {
            put(svc, "latency_p99", "ms", Shape.ramp(onset, rampEnd, p99Before, p99After), p99After * 0.06);
            put(svc, "latency_p95", "ms", Shape.ramp(onset, rampEnd, p99Before * 0.6, p99After * 0.7), p99After * 0.05);
            put(svc, "latency_p50", "ms", Shape.ramp(onset, rampEnd, p99Before * 0.3, p99After * 0.35), p99Before * 0.15);
        }

        /**
         * Toda métrica degradada passa por aqui. Quando o incidente foi mitigado, a forma volta ao valor que ela
         * tinha logo antes do início — é assim que a ação do usuário aparece no gráfico.
         */
        private void put(String svc, String metric, String unit, Shape shape, double noise) {
            Shape painted = recoveredAt == null
                    ? shape
                    : shape.recoveringTo(Shape.constant(shape.valueAt(onset.minusSeconds(60))), recoveredAt, RECOVERY);
            MetricSeries s = builder.series(svc, metric, unit, painted, noise);
            metrics.put(s.key(), s);
        }

        private void errorLines(int perMinute, String level, List<String> messages, Map<String, String> fields) {
            Map<String, String> all = new LinkedHashMap<>(fields);
            all.putIfAbsent("pod", service + "-" + random.nextInt(1, 4));
            all.putIfAbsent("endpoint", sc.paramOr("endpoint", "/v1/resource"));
            Instant until = recoveredAt == null ? window.to() : min(window.to(), recoveredAt.plus(RECOVERY));
            logLines.addAll(logs.incidentLines(service, onset, until, perMinute, level, messages, Math.round(p99After), Map.copyOf(all), List.of()));
        }

        private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

        private void dominatedTrace(String slowOperation, long slowMs, Map<String, String> tags) {
            long seconds = Math.max(60, Duration.between(onset, now).getSeconds());
            for (int i = 0; i < 5; i++) {
                traceList.add(traces.dominatedBy(logs.traceId(), service, operationName(sc),
                        tags.containsKey("peer.service") ? tags.get("peer.service") : datastore, slowOperation, slowMs,
                        onset.plusSeconds(random.nextLong(30, seconds)), tags));
            }
        }

        private void alert(String name, String svc, String severity, String description) {
            alerts.add(new Alert(onset.plus(Duration.ofMinutes(random.nextInt(2, 6))), name, svc, severity, description, null));
        }
    }

    private static Instant latest(Instant a, Instant b) { return a.isAfter(b) ? a : b; }

    /** The endpoint in the scenario may already carry the verb ("GET /accounts/{id}/statement"). */
    private static String operationName(ScenarioSpec sc) {
        String endpoint = sc.paramOr("endpoint", "/v1/resource");
        return endpoint.matches("(?i)^(GET|POST|PUT|PATCH|DELETE|HEAD)\\s.*") ? endpoint : "GET " + endpoint;
    }

    // ---------------------------------------------------------------- timeline and state

    /** The culprit deploy plus unrelated ones: "what changed" must require reading, not guessing. */
    private static List<DeployEvent> deploys(ScenarioSpec sc, Company company, RandomSource random, TimeWindow window, Instant onset) {
        List<DeployEvent> out = new ArrayList<>();
        String deployAt = sc.paramOr("deployAt", null);
        if (deployAt != null) {
            out.add(new DeployEvent(Instant.parse(deployAt), sc.paramOr("deployService", sc.param("service")),
                    sc.paramOr("deployVersion", "2026.37.1"), sc.paramOr("deployAuthor", "squad-" + sc.param("service")),
                    sc.paramOr("deployDescription", "release da sprint"), false));
        }
        List<ServiceComponent> services = company.architecture().services();
        int noise = random.nextInt(2, 5);
        for (int i = 0; i < noise && !services.isEmpty(); i++) {
            Instant at = window.from().plusSeconds(random.nextLong(0, Math.max(60, window.duration().getSeconds() - 600)));
            ServiceComponent s = random.pick(services);
            out.add(new DeployEvent(at, s.name(), "2026.37." + random.nextInt(0, 9), "squad-" + s.name(),
                    random.pick(List.of("bump de dependências", "ajuste de log", "correção de typo no contrato", "atualização de métricas",
                            "rollout de canário 10%", "ajuste de timeout do cliente HTTP")), false));
        }
        out.sort(Comparator.comparing(DeployEvent::at));
        return List.copyOf(out);
    }

    private static List<FeatureFlagChange> flags(ScenarioSpec sc, IncidentRootCause cause, RandomSource random, TimeWindow window, Instant onset) {
        List<FeatureFlagChange> out = new ArrayList<>();
        if (cause == IncidentRootCause.FEATURE_FLAG) {
            out.add(new FeatureFlagChange(onset, sc.paramOr("flag", "new-pricing-engine"), "off", "20%", "produto", sc.param("service")));
        }
        for (int i = 0; i < random.nextInt(1, 3); i++) {
            out.add(new FeatureFlagChange(window.from().plusSeconds(random.nextLong(0, window.duration().getSeconds())),
                    random.pick(List.of("new-onboarding-ui", "pix-copy-paste-v2", "statement-pagination", "dark-mode")),
                    "off", "on", random.pick(List.of("produto", "plataforma")), "global"));
        }
        out.sort(Comparator.comparing(FeatureFlagChange::at));
        return List.copyOf(out);
    }

    private static List<InstanceStatus> instances(Company company, String service, IncidentRootCause cause, RandomSource random, TimeWindow window, Instant now) {
        List<InstanceStatus> out = new ArrayList<>();
        List<ServiceComponent> services = new ArrayList<>(company.architecture().services());
        if (services.stream().noneMatch(s -> s.name().equals(service))) {
            services.add(new ServiceComponent(service, "", "Java", List.of(), null, 3));
        }
        for (ServiceComponent s : services) {
            int count = Math.max(2, Math.min(10, s.instances()));
            for (int i = 0; i < count; i++) {
                boolean affected = s.name().equals(service);
                int restarts = affected && cause == IncidentRootCause.MEMORY_LEAK ? random.nextInt(1, 4) : 0;
                String status = restarts > 0 && i == 0 ? "CrashLoopBackOff" : "Running";
                double cpu = affected ? random.nextInt(55, 95) : random.nextInt(18, 55);
                double mem = affected && cause == IncidentRootCause.MEMORY_LEAK ? random.nextInt(88, 99) : random.nextInt(45, 80);
                out.add(new InstanceStatus(s.name(), s.name() + "-" + random.nextInt(100000, 999999) + "-" + i, status, cpu, mem, restarts,
                        now.minusSeconds(random.nextLong(3600, 86_400))));
            }
        }
        return List.copyOf(out);
    }

    private static DbState dbState(Company company, String service, IncidentRootCause cause, ScenarioSpec sc, RandomSource random, Instant now, Instant onset) {
        DatabaseSchema schema = company.schema();
        String index = sc.paramOr("index", null);
        String table = sc.paramOr("table", schema.tables().isEmpty() ? null : schema.tables().getFirst().name());
        if (cause == IncidentRootCause.INDEX_REMOVED && index != null && table != null) {
            schema = schema.withTable(schema.table(table).orElseThrow().withoutIndex(index));
        }

        List<ActiveSession> sessions = new ArrayList<>();
        List<LockInfo> locks = new ArrayList<>();
        int pid = 41_000 + random.nextInt(0, 900);
        String slowSql = sc.paramOr("slowSql", table == null ? "SELECT 1" : "SELECT * FROM " + table + " WHERE account_id = $1");
        int busy = switch (cause) {
            case POOL_EXHAUSTION, N_PLUS_ONE, INDEX_REMOVED, LONG_LOCK -> random.nextInt(8, 18);
            default -> random.nextInt(1, 4);
        };
        for (int i = 0; i < busy; i++) {
            long durationMs = random.nextLong(800, 42_000);
            String waitType = cause == IncidentRootCause.LONG_LOCK ? "Lock" : "IO";
            String waitEvent = cause == IncidentRootCause.LONG_LOCK ? "transactionid" : "DataFileRead";
            sessions.add(new ActiveSession(pid + i, "app_" + service.replace('-', '_'), service, "10.0." + random.nextInt(1, 4) + "." + random.nextInt(10, 250),
                    "active", waitType, waitEvent, slowSql.replace("$1", String.valueOf(random.nextInt(1000, 999_999))),
                    now.minusMillis(durationMs), durationMs));
        }
        if (cause == IncidentRootCause.LONG_LOCK && table != null) {
            long heldMs = Duration.between(onset, now).toMillis() + 600_000;
            sessions.add(new ActiveSession(pid + 900, "app_batch", "reconciliation-batch", "10.0.9.12", "idle in transaction", "Client", "ClientRead",
                    "UPDATE " + table + " SET status = 'RECONCILED' WHERE batch_id = $1", now.minusMillis(heldMs), heldMs));
            locks.add(new LockInfo(pid + 900, table, "transactionid", "ExclusiveLock", true, 0));
            for (int i = 0; i < Math.min(6, busy); i++) locks.add(new LockInfo(pid + i, table, "transactionid", "ShareLock", false, random.nextLong(4000, 90_000)));
        }
        for (int i = 0; i < random.nextInt(4, 10); i++) {
            String svc = company.architecture().services().isEmpty() ? service : random.pick(company.architecture().services()).name();
            sessions.add(new ActiveSession(pid + 100 + i, "app_" + svc.replace('-', '_'), svc, "10.0." + random.nextInt(1, 4) + "." + random.nextInt(10, 250),
                    "idle", "Client", "ClientRead", "COMMIT", now.minusSeconds(random.nextInt(1, 120)), random.nextInt(1, 40)));
        }

        List<StatementStat> statements = new ArrayList<>();
        boolean dbHeavy = switch (cause) {
            case N_PLUS_ONE, INDEX_REMOVED, LONG_LOCK, POOL_EXHAUSTION, CACHE_STAMPEDE -> true;
            default -> false;
        };
        if (dbHeavy && table != null) {
            long calls = random.nextLong(60_000, 900_000);
            double mean = cause == IncidentRootCause.N_PLUS_ONE ? random.nextInt(3, 9) : random.nextInt(900, 4200);
            statements.add(new StatementStat(slowSql, calls, calls * mean, mean, mean * 1.7, calls, calls * 40, calls * 12));
        }
        for (Table t : schema.tables()) {
            String pk = t.columns().isEmpty() ? "id" : t.columns().getFirst().name();
            long c = random.nextLong(20_000, 2_000_000);
            double m = 0.2 + random.nextDouble() * 3;
            statements.add(new StatementStat("SELECT * FROM " + t.name() + " WHERE " + pk + " = $1", c, c * m, m, m * 8, c, c / 3, c * 4));
            long c2 = random.nextLong(5_000, 400_000);
            double m2 = 1 + random.nextDouble() * 9;
            statements.add(new StatementStat("UPDATE " + t.name() + " SET updated_at = now() WHERE " + pk + " = $1", c2, c2 * m2, m2, m2 * 6, c2, c2 / 5, c2 * 3));
        }
        statements.add(new StatementStat("COMMIT", random.nextLong(500_000, 9_000_000), 90_000, 0.05, 3.1, 0, 0, 0));
        statements.sort((a, b) -> Double.compare(b.meanMs(), a.meanMs()));

        List<TableStat> tableStats = new ArrayList<>();
        for (Table t : schema.tables()) {
            boolean target = table != null && t.name().equalsIgnoreCase(table);
            long seq = target && dbHeavy ? random.nextLong(4_000, 90_000) : random.nextLong(0, 60);
            tableStats.add(new TableStat(t.name(), seq, seq * Math.min(t.rowCount(), 5_000_000), random.nextLong(100_000, 90_000_000),
                    t.rowCount(), t.rowCount() / random.nextInt(40, 400), t.sizeMb(), now.minus(Duration.ofHours(random.nextInt(2, 30)))));
        }
        return new DbState(List.copyOf(sessions), List.copyOf(locks), List.copyOf(statements), List.copyOf(tableStats), schema);
    }
}
