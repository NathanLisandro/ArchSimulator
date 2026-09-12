package dev.archsim.domain.shared;

import java.util.List;
import java.util.Random;

public final class SeededRandom implements RandomSource {
    private final long seed;
    private final Random random;

    public SeededRandom(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    @Override public int nextInt(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("max < min");
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    @Override public long nextLong(long minInclusive, long maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("max < min");
        return minInclusive + random.nextLong(maxInclusive - minInclusive + 1);
    }

    @Override public double nextDouble() { return random.nextDouble(); }
    @Override public double nextGaussian() { return random.nextGaussian(); }

    @Override public <T> T pick(List<T> options) {
        if (options.isEmpty()) throw new IllegalArgumentException("empty options");
        return options.get(random.nextInt(options.size()));
    }

    @Override public RandomSource fork(String label) {
        return new SeededRandom(seed * 31L + label.hashCode() * 0x9E3779B97F4A7C15L);
    }

    @Override public long seed() { return seed; }
}
