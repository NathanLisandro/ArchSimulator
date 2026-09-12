package dev.archsim.domain.observability;

import java.util.List;

public record MetricSeries(String service, String metric, String unit, List<MetricPoint> points) {
    public double max() { return points.stream().mapToDouble(MetricPoint::value).max().orElse(0); }
    public double avg() { return points.stream().mapToDouble(MetricPoint::value).average().orElse(0); }
    public double last() { return points.isEmpty() ? 0 : points.getLast().value(); }
    public String key() { return service + ":" + metric; }
}
