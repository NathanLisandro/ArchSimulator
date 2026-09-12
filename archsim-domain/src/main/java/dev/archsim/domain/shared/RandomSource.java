package dev.archsim.domain.shared;

import java.util.List;

/** Deterministic random port. Implementations must be reproducible from a seed. */
public interface RandomSource {
    int nextInt(int minInclusive, int maxInclusive);
    long nextLong(long minInclusive, long maxInclusive);
    double nextDouble();
    double nextGaussian();
    <T> T pick(List<T> options);
    /** Derives an independent, reproducible sub-stream identified by label. */
    RandomSource fork(String label);
    long seed();
}
