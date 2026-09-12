package dev.archsim.domain.template;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.shared.SeededRandom;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaseGeneratorTest {
    private static CaseTemplate template() { return dev.archsim.domain.fixtures.Fixtures.slowQueryTemplate(); }

    private final CaseGenerator generator = new CaseGenerator();
    private final Instant now = Instant.parse("2026-09-12T15:00:00Z");

    @Test
    void rendersParametersConsistentlyAcrossAllTexts() {
        Case c = generator.generate(template(), Level.JUNIOR, new SeededRandom(5L), now);
        assertThat(c.narrative().title()).doesNotContain("{{");
        assertThat(c.narrative().symptom()).doesNotContain("{{").containsPattern("\\d{1,3}(\\.\\d{3})+ linhas");
        assertThat(c.checkpoints().getFirst().options().get(1).feedback()).doesNotContain("{{").containsAnyOf("account-api", "receivables-api");
        assertThat(c.solutionPaths().getFirst().cons().getFirst()).doesNotContain("{{");
        assertThat(c.referenceAnswer().markdown()).doesNotContain("{{");
        assertThat(c.scenario().params().get("service")).isIn("account-api", "receivables-api");
        assertThat(c.scenario().params().get("sinceAt")).startsWith("2026-09-1");
        assertThat(c.params().get("p99Before")).isNotNull();
        assertThat(c.type()).isEqualTo(CaseType.SLOW_QUERY);
        assertThat(c.level()).isEqualTo(Level.JUNIOR);
        assertThat(c.tools()).contains(ConsoleTool.SQL);
    }

    @Test
    void sameSeedSameCaseDifferentSeedDifferentCase() {
        Case a = generator.generate(template(), Level.JUNIOR, new SeededRandom(5L), now);
        Case b = generator.generate(template(), Level.JUNIOR, new SeededRandom(5L), now);
        Case c = generator.generate(template(), Level.JUNIOR, new SeededRandom(6L), now);
        assertThat(a.params()).isEqualTo(b.params());
        assertThat(a.params()).isNotEqualTo(c.params());
    }

    @Test
    void recommendationRuleOverridesDefaultPath() {
        Case big = null;
        for (long seed = 0; seed < 200 && big == null; seed++) {
            Case c = generator.generate(template(), Level.JUNIOR, new SeededRandom(seed), now);
            if (Long.parseLong(c.params().get("rows")) > 100_000_000) big = c;
        }
        assertThat(big).isNotNull();
        assertThat(big.recommendation().pathId()).isEqualTo("cache");
        assertThat(big.recommendation().reason()).doesNotContain("{{").contains("M linhas");
    }

    @Test
    void hintsAreHiddenFromSeniorLevel() {
        assertThat(generator.generate(template(), Level.JUNIOR, new SeededRandom(1L), now).narrative().hints()).isNotEmpty();
        assertThat(generator.generate(template(), Level.SENIOR, new SeededRandom(1L), now).narrative().hints()).isEmpty();
    }

    @Test
    void intRangeRespectsStep() {
        for (long seed = 0; seed < 30; seed++) {
            Case c = generator.generate(template(), Level.JUNIOR, new SeededRandom(seed), now);
            assertThat(Long.parseLong(c.params().get("p99")) % 100).isZero();
            assertThat(Long.parseLong(c.params().get("p99"))).isBetween(3000L, 6000L);
        }
    }
}
