package dev.archsim.domain.shared;

import java.time.Duration;
import java.time.Instant;

public record TimeWindow(Instant from, Instant to) {
    public TimeWindow {
        if (!to.isAfter(from)) throw new IllegalArgumentException("window must be positive");
    }
    public Duration duration() { return Duration.between(from, to); }
    public boolean contains(Instant t) { return !t.isBefore(from) && !t.isAfter(to); }
    public static TimeWindow of(Instant from, Duration length) { return new TimeWindow(from, from.plus(length)); }
}
