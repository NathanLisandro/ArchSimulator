package dev.archsim.domain.observability;

import dev.archsim.domain.shared.TimeWindow;

import java.util.List;
import java.util.Optional;

/** Everything the simulated console can show for one case, sharing one time axis and trace ids. Regenerated from seed (ADR-0002). */
public record ObservabilityDataset(TimeWindow window, List<MetricSeries> metrics, List<LogEntry> logs, List<Trace> traces,
                                   List<DeployEvent> deploys, List<FeatureFlagChange> flagChanges, List<Alert> alerts,
                                   List<InstanceStatus> instances, DbState dbState) {
    public Optional<MetricSeries> series(String service, String metric) {
        return metrics.stream().filter(s -> s.service().equals(service) && s.metric().equals(metric)).findFirst();
    }
    public List<String> services() { return metrics.stream().map(MetricSeries::service).distinct().toList(); }
    public List<String> metricNames(String service) {
        return metrics.stream().filter(s -> s.service().equals(service)).map(MetricSeries::metric).distinct().toList();
    }
}
