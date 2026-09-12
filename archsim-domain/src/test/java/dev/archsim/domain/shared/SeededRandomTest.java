package dev.archsim.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeededRandomTest {

    @Test
    void sameSeedProducesSameSequence() {
        RandomSource a = new SeededRandom(42L);
        RandomSource b = new SeededRandom(42L);
        for (int i = 0; i < 20; i++) {
            assertThat(a.nextInt(1, 1000)).isEqualTo(b.nextInt(1, 1000));
        }
        assertThat(a.pick(List.of("x", "y", "z"))).isEqualTo(b.pick(List.of("x", "y", "z")));
    }

    @Test
    void differentSeedsDiverge() {
        RandomSource a = new SeededRandom(1L);
        RandomSource b = new SeededRandom(2L);
        boolean anyDifferent = false;
        for (int i = 0; i < 10; i++) anyDifferent |= a.nextInt(0, 1_000_000) != b.nextInt(0, 1_000_000);
        assertThat(anyDifferent).isTrue();
    }

    @Test
    void intRangeIsInclusive() {
        RandomSource r = new SeededRandom(7L);
        for (int i = 0; i < 500; i++) assertThat(r.nextInt(3, 5)).isBetween(3, 5);
        assertThat(r.nextInt(9, 9)).isEqualTo(9);
    }

    @Test
    void forkIsDeterministicAndIndependentOfParentConsumption() {
        RandomSource parent1 = new SeededRandom(99L);
        RandomSource parent2 = new SeededRandom(99L);
        parent2.nextInt(0, 10); // consume from parent2 only
        assertThat(parent1.fork("metrics").nextLong(0, 1_000_000L))
                .isEqualTo(parent2.fork("metrics").nextLong(0, 1_000_000L));
        assertThat(parent1.fork("metrics").nextLong(0, 1_000_000L))
                .isNotEqualTo(parent1.fork("logs").nextLong(0, 1_000_000L));
    }
}
