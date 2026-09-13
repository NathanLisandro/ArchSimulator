package dev.archsim.domain.observability;

import java.time.Duration;
import java.time.Instant;

/** Composable time → value function used to synthesise recognisable metric patterns. */
@FunctionalInterface
public interface Shape {
    double valueAt(Instant t);

    static Shape constant(double v) { return t -> v; }

    /** Value jumps from before to after at the given instant. */
    static Shape step(Instant at, double before, double after) { return t -> t.isBefore(at) ? before : after; }

    /** Linear ramp between two instants; flat outside. */
    static Shape ramp(Instant start, Instant end, double from, double to) {
        return t -> {
            if (!t.isAfter(start)) return from;
            if (!t.isBefore(end)) return to;
            double f = (double) (t.toEpochMilli() - start.toEpochMilli()) / (end.toEpochMilli() - start.toEpochMilli());
            return from + (to - from) * f;
        };
    }

    /** Adds peak during [at, at+duration], zero elsewhere. */
    static Shape spike(Instant at, Duration duration, double peak) {
        Instant end = at.plus(duration);
        return t -> (!t.isBefore(at) && !t.isAfter(end)) ? peak : 0;
    }

    /** GC-like sawtooth: rises linearly from min to max over each period, then resets. */
    static Shape sawtooth(Instant start, Duration period, double min, double max) {
        long p = period.toMillis();
        return t -> {
            long elapsed = t.toEpochMilli() - start.toEpochMilli();
            long inPeriod = ((elapsed % p) + p) % p;
            return min + (max - min) * inPeriod / p;
        };
    }

    /** Daily-traffic-like curve: base + amplitude * sin over 24h peaking at peakHourUtc. */
    static Shape daily(double base, double amplitude, int peakHourUtc) {
        return t -> {
            double hour = (t.getEpochSecond() % 86400) / 3600.0;
            return base + amplitude * Math.sin((hour - peakHourUtc + 6) * Math.PI / 12);
        };
    }

    /**
     * A partir de {@code at}, transita desta forma de volta para {@code normal} ao longo de {@code duration}.
     * É o que faz a mitigação aplicada pelo usuário aparecer nos gráficos como recuperação, e não como corte seco.
     */
    default Shape recoveringTo(Shape normal, Instant at, Duration duration) {
        Instant end = at.plus(duration);
        return t -> {
            if (t.isBefore(at)) return valueAt(t);
            if (!t.isBefore(end)) return normal.valueAt(t);
            double f = (double) (t.toEpochMilli() - at.toEpochMilli()) / Math.max(1, end.toEpochMilli() - at.toEpochMilli());
            return valueAt(t) * (1 - f) + normal.valueAt(t) * f;
        };
    }

    default Shape plus(Shape other) { return t -> valueAt(t) + other.valueAt(t); }
    default Shape times(Shape other) { return t -> valueAt(t) * other.valueAt(t); }
    default Shape clamp(double min, double max) { return t -> Math.max(min, Math.min(max, valueAt(t))); }
}
