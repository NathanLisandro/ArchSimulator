package dev.archsim.domain.observability;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.ScenarioSpec;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.Datastore;
import dev.archsim.domain.company.ServiceComponent;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenario "capacity": 90 days of history at 1 point per hour, showing the growth the user has to extrapolate —
 * traffic climbing at a monthly rate, a resource approaching a hard limit (connections, IOPS, storage, partitions)
 * and latency that is still fine today. There is no incident here: the exercise is seeing the wall before hitting it.
 *
 * <p>Params: service, datastore, growthPercentPerMonth, currentPeakRps, headroomMetric, headroomLimit, headroomNow.
 */
public final class CapacityDatasetGenerator implements ScenarioDatasetGenerator {
    private static final Duration WINDOW = Duration.ofDays(90);
    private static final Duration STEP = Duration.ofHours(1);

    @Override public String kind() { return "capacity"; }

    @Override
    public ObservabilityDataset generate(Case theCase, Company company, RandomSource random) {
        ScenarioSpec sc = theCase.scenario();
        Instant now = theCase.generatedAt();
        TimeWindow window = new TimeWindow(now.minus(WINDOW), now);
        String service = sc.param("service");
        String datastore = sc.paramOr("datastore", company.architecture().datastores().isEmpty() ? "core-db" : company.architecture().datastores().getFirst().name());
        double growthPerMonth = Double.parseDouble(sc.paramOr("growthPercentPerMonth", "8")) / 100.0;
        double currentPeakRps = Double.parseDouble(sc.paramOr("currentPeakRps", "800"));
        String headroomMetric = sc.paramOr("headroomMetric", "active_connections");
        double headroomLimit = Double.parseDouble(sc.paramOr("headroomLimit", "5000"));
        double headroomNow = Double.parseDouble(sc.paramOr("headroomNow", "3200"));

        SeriesBuilder builder = new SeriesBuilder(window, STEP, random.fork("noise"));
        double startRps = currentPeakRps / Math.pow(1 + growthPerMonth, 3);
        Shape traffic = growth(window, startRps, currentPeakRps).times(Shape.daily(1, 0.45, 15).clamp(0.35, 1.6));

        List<MetricSeries> metrics = new ArrayList<>();
        metrics.add(builder.series(service, "requests_per_second", "rps", traffic, currentPeakRps * 0.06));
        metrics.add(builder.series(service, "latency_p50", "ms", Shape.constant(42), 6));
        metrics.add(builder.series(service, "latency_p99", "ms",
                growth(window, 190, 190 * (1 + growthPerMonth * 1.6)).clamp(100, 4000), 30));
        metrics.add(builder.series(service, "error_rate", "%", Shape.constant(0.2), 0.15));
        metrics.add(builder.series(service, "cpu_utilization", "%", growth(window, 38, 38 * (1 + growthPerMonth * 3)).clamp(0, 100), 5));
        metrics.add(builder.series(datastore, headroomMetric, unitFor(headroomMetric),
                growth(window, headroomNow / Math.pow(1 + growthPerMonth, 3), headroomNow), headroomNow * 0.04));
        metrics.add(builder.series(datastore, headroomMetric + "_limit", unitFor(headroomMetric), Shape.constant(headroomLimit), 0));
        metrics.add(builder.series(datastore, "cpu_utilization", "%", growth(window, 34, 34 * (1 + growthPerMonth * 3)).clamp(0, 100), 4));
        metrics.add(builder.series(datastore, "storage_used", "GB",
                growth(window, Double.parseDouble(sc.paramOr("storageNowGb", "42000")) * 0.78, Double.parseDouble(sc.paramOr("storageNowGb", "42000"))), 120));

        // Other services grow too, so the reader cannot assume the bottleneck is the obvious one.
        for (ServiceComponent s : company.architecture().services()) {
            if (s.name().equals(service)) continue;
            double base = random.nextInt(60, 400);
            metrics.add(builder.series(s.name(), "requests_per_second", "rps",
                    growth(window, base, base * (1 + growthPerMonth * 0.6)).times(Shape.daily(1, 0.4, 15).clamp(0.4, 1.5)), base * 0.08));
            metrics.add(builder.series(s.name(), "latency_p99", "ms", Shape.constant(random.nextInt(120, 320)), 25));
        }
        for (Datastore d : company.architecture().datastores()) {
            if (d.name().equals(datastore)) continue;
            metrics.add(builder.series(d.name(), "cpu_utilization", "%", Shape.constant(random.nextInt(20, 45)), 5));
        }

        return new ObservabilityDataset(window, List.copyOf(metrics), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new DbState(List.of(), List.of(), List.of(), List.of(), company.schema()));
    }

    private static Shape growth(TimeWindow window, double from, double to) {
        return Shape.ramp(window.from(), window.to(), from, to);
    }

    private static String unitFor(String metric) {
        return switch (metric) {
            case "active_connections" -> "connections";
            case "read_iops", "write_iops" -> "iops";
            case "storage_used" -> "GB";
            case "consumer_lag" -> "messages";
            default -> "";
        };
    }
}
