package dev.archsim.domain.observability;

import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Samples shapes over a window at a fixed step, adding deterministic gaussian noise. */
public final class SeriesBuilder {
    private final TimeWindow window;
    private final Duration step;
    private final RandomSource random;

    public SeriesBuilder(TimeWindow window, Duration step, RandomSource random) {
        this.window = window;
        this.step = step;
        this.random = random;
    }

    public MetricSeries series(String service, String metric, String unit, Shape shape, double noiseAmplitude) {
        List<MetricPoint> points = new ArrayList<>();
        for (Instant t = window.from(); !t.isAfter(window.to()); t = t.plus(step)) {
            double noise = noiseAmplitude == 0 ? 0 : random.nextGaussian() * noiseAmplitude / 3.0;
            double v = shape.valueAt(t) + noise;
            points.add(new MetricPoint(t, Math.round(Math.max(0, v) * 100.0) / 100.0));
        }
        return new MetricSeries(service, metric, unit, List.copyOf(points));
    }

    public TimeWindow window() { return window; }
}
