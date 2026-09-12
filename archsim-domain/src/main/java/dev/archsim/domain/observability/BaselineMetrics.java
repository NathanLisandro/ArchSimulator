package dev.archsim.domain.observability;

import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.Datastore;
import dev.archsim.domain.company.ServiceComponent;
import dev.archsim.domain.shared.RandomSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Healthy-looking metrics for every service and datastore of a company: the noise every incident hides in. */
public final class BaselineMetrics {
    private final SeriesBuilder builder;
    private final RandomSource random;

    public BaselineMetrics(SeriesBuilder builder, RandomSource random) { this.builder = builder; this.random = random; }

    public List<MetricSeries> forCompany(Company company) {
        List<MetricSeries> out = new ArrayList<>();
        for (ServiceComponent s : company.architecture().services()) out.addAll(forService(s));
        for (Datastore d : company.architecture().datastores()) out.addAll(forDatastore(d));
        return out;
    }

    public List<MetricSeries> forService(ServiceComponent s) {
        double baseP50 = random.nextInt(25, 70);
        double baseRps = random.nextInt(40, 400);
        Shape traffic = Shape.daily(baseRps, baseRps * 0.5, 15).clamp(1, Double.MAX_VALUE);
        return List.of(
                builder.series(s.name(), "requests_per_second", "rps", traffic, baseRps * 0.15),
                builder.series(s.name(), "latency_p50", "ms", Shape.constant(baseP50), baseP50 * 0.2),
                builder.series(s.name(), "latency_p95", "ms", Shape.constant(baseP50 * 2.4), baseP50 * 0.5),
                builder.series(s.name(), "latency_p99", "ms", Shape.constant(baseP50 * 4.0), baseP50 * 1.2),
                builder.series(s.name(), "error_rate", "%", Shape.constant(0.2), 0.3),
                builder.series(s.name(), "cpu_utilization", "%", Shape.daily(35, 15, 15), 8),
                builder.series(s.name(), "memory_used", "%", Shape.sawtooth(builder.window().from(), Duration.ofMinutes(45), 55, 78), 2),
                builder.series(s.name(), "db_pool_in_use", "connections", Shape.daily(6, 3, 15).clamp(0, 20), 2),
                builder.series(s.name(), "gc_pause_p99", "ms", Shape.constant(18), 12));
    }

    public List<MetricSeries> forDatastore(Datastore d) {
        return List.of(
                builder.series(d.name(), "cpu_utilization", "%", Shape.daily(30, 12, 15), 6),
                builder.series(d.name(), "read_iops", "iops", Shape.daily(1800, 900, 15), 300),
                builder.series(d.name(), "write_iops", "iops", Shape.daily(700, 300, 15), 120),
                builder.series(d.name(), "read_latency", "ms", Shape.constant(1.8), 0.6),
                builder.series(d.name(), "active_connections", "connections", Shape.daily(60, 25, 15), 8),
                builder.series(d.name(), "cache_hit_ratio", "%", Shape.constant(98.5), 0.8),
                builder.series(d.name(), "burst_balance", "%", Shape.constant(100), 0));
    }

    public SeriesBuilder builder() { return builder; }
}
