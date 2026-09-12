package dev.archsim.domain.observability;

import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.shared.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesBuilderTest {
    private final Instant start = Instant.parse("2026-09-12T00:00:00Z");
    private final TimeWindow window = TimeWindow.of(start, Duration.ofHours(2));

    @Test
    void stepShapeChangesValueAtInstant() {
        Shape s = Shape.step(start.plusSeconds(3600), 180, 4200);
        assertThat(s.valueAt(start.plusSeconds(3599))).isEqualTo(180);
        assertThat(s.valueAt(start.plusSeconds(3600))).isEqualTo(4200);
    }

    @Test
    void rampSpikeAndSawtoothBehave() {
        Shape ramp = Shape.ramp(start, start.plusSeconds(1000), 0, 100);
        assertThat(ramp.valueAt(start.plusSeconds(500))).isEqualTo(50);
        assertThat(ramp.valueAt(start.plusSeconds(2000))).isEqualTo(100);
        Shape spike = Shape.spike(start.plusSeconds(100), Duration.ofSeconds(60), 900);
        assertThat(spike.valueAt(start.plusSeconds(99))).isZero();
        assertThat(spike.valueAt(start.plusSeconds(130))).isEqualTo(900);
        assertThat(spike.valueAt(start.plusSeconds(161))).isZero();
        Shape saw = Shape.sawtooth(start, Duration.ofSeconds(100), 40, 90);
        assertThat(saw.valueAt(start)).isEqualTo(40);
        assertThat(saw.valueAt(start.plusSeconds(99))).isCloseTo(89.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(saw.valueAt(start.plusSeconds(100))).isEqualTo(40);
        Shape sum = Shape.constant(10).plus(Shape.constant(5)).clamp(0, 12);
        assertThat(sum.valueAt(start)).isEqualTo(12);
    }

    @Test
    void builderSamplesDeterministicallyWithNoise() {
        SeriesBuilder b1 = new SeriesBuilder(window, Duration.ofMinutes(1), new SeededRandom(1L));
        SeriesBuilder b2 = new SeriesBuilder(window, Duration.ofMinutes(1), new SeededRandom(1L));
        MetricSeries s1 = b1.series("account-api", "latency_p99", "ms", Shape.constant(180), 10);
        MetricSeries s2 = b2.series("account-api", "latency_p99", "ms", Shape.constant(180), 10);
        assertThat(s1.points()).hasSize(121);
        assertThat(s1.points()).isEqualTo(s2.points());
        assertThat(s1.points()).allSatisfy(p -> assertThat(p.value()).isBetween(150.0, 210.0));
        assertThat(s1.points().stream().mapToDouble(MetricPoint::value).distinct().count()).isGreaterThan(20);
        assertThat(s1.max()).isGreaterThan(180);
    }
}
