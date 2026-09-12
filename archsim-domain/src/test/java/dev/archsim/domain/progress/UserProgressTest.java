package dev.archsim.domain.progress;

import dev.archsim.domain.attempt.AttemptEvaluated;
import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserProgressTest {
    private static final Instant T0 = Instant.parse("2026-09-12T12:00:00Z");
    private final UserId user = new UserId("u1");
    private final UserProgress progress = UserProgress.start(user, T0);

    private AttemptEvaluated attempt(CaseType type, double score, List<String> competencies, long seconds) {
        return new AttemptEvaluated(AttemptId.random(), user, CaseId.random(), type, competencies, score, seconds, T0);
    }

    @Test
    void startsAsJuniorWithNoHistory() {
        assertThat(progress.rank()).isEqualTo(Level.JUNIOR);
        assertThat(progress.xp()).isZero();
        assertThat(progress.solvedCount()).isZero();
        assertThat(progress.statsByType()).isEmpty();
        assertThat(progress.weakestCompetencies(3)).isEmpty();
    }

    @Test
    void recordsScoreAveragesPerTypeAndPerCompetency() {
        progress.record(attempt(CaseType.SLOW_QUERY, 80, List.of("databases", "observability"), 600), T0);
        progress.record(attempt(CaseType.SLOW_QUERY, 60, List.of("databases"), 500), T0);
        progress.record(attempt(CaseType.INCIDENT, 40, List.of("incident", "observability"), 900), T0);

        assertThat(progress.solvedCount()).isEqualTo(3);
        TypeStats slow = progress.statsByType().get(CaseType.SLOW_QUERY);
        assertThat(slow.attempts()).isEqualTo(2);
        assertThat(slow.averageScore()).isEqualTo(70);
        assertThat(slow.bestScore()).isEqualTo(80);
        assertThat(progress.statsByType().get(CaseType.INCIDENT).averageScore()).isEqualTo(40);
        assertThat(progress.averageScore()).isEqualTo(60);
        assertThat(progress.competencyScore("databases")).isEqualTo(70);
        assertThat(progress.competencyScore("observability")).isEqualTo(60);
        assertThat(progress.weakestCompetencies(2)).containsExactly("incident", "observability");
        assertThat(progress.strongestCompetencies(1)).containsExactly("databases");
    }

    @Test
    void xpRewardsScoreAndPenalisesNothingBelowThreshold() {
        progress.record(attempt(CaseType.ADR, 100, List.of("adr"), 600), T0);
        assertThat(progress.xp()).isEqualTo(100);
        progress.record(attempt(CaseType.ADR, 30, List.of("adr"), 600), T0);
        assertThat(progress.xp()).isEqualTo(130);
    }

    @Test
    void promotesOnlyWithEnoughVolumeQualityAndBreadth() {
        for (int i = 0; i < 7; i++) progress.record(attempt(CaseType.SLOW_QUERY, 95, List.of("databases"), 600), T0);
        assertThat(progress.rank()).as("sete casos de um tipo só não formam um pleno").isEqualTo(Level.JUNIOR);

        progress.record(attempt(CaseType.ADR, 90, List.of("adr"), 600), T0);
        progress.record(attempt(CaseType.INCIDENT, 85, List.of("incident"), 600), T0);
        progress.record(attempt(CaseType.CODE_REVIEW, 80, List.of("solid"), 600), T0);

        assertThat(progress.rank()).isEqualTo(Level.PLENO);
        assertThat(progress.pullEvents()).anySatisfy(e -> assertThat(e).isInstanceOf(RankPromoted.class));
    }

    @Test
    void lowScoresBlockPromotionEvenWithVolume() {
        for (CaseType type : CaseType.values()) {
            for (int i = 0; i < 4; i++) progress.record(attempt(type, 45, List.of("x"), 600), T0);
        }
        assertThat(progress.solvedCount()).isEqualTo(CaseType.values().length * 4);
        assertThat(progress.rank()).isEqualTo(Level.JUNIOR);
    }

    @Test
    void climbsToStaffAndNeverDemotes() {
        for (int round = 0; round < 12; round++) {
            for (CaseType type : CaseType.values()) progress.record(attempt(type, 92, List.of("adr", "eda"), 600), T0);
        }
        assertThat(progress.rank()).isEqualTo(Level.STAFF);

        for (int i = 0; i < 20; i++) progress.record(attempt(CaseType.SLOW_QUERY, 10, List.of("databases"), 600), T0);
        assertThat(progress.rank()).as("cargo conquistado não é perdido por uma sequência ruim").isEqualTo(Level.STAFF);
    }

    @Test
    void promotionEventCarriesFromAndTo() {
        for (int i = 0; i < 4; i++) {
            for (CaseType type : CaseType.values()) progress.record(attempt(type, 90, List.of("adr"), 600), T0);
        }
        List<DomainEvent> events = progress.pullEvents();
        assertThat(events).isNotEmpty();
        RankPromoted first = (RankPromoted) events.getFirst();
        assertThat(first.from()).isEqualTo(Level.JUNIOR);
        assertThat(first.to()).isEqualTo(Level.PLENO);
        assertThat(progress.pullEvents()).isEmpty();
    }

    @Test
    void suggestsTheNextCaseTypeFromTheWeakestArea() {
        progress.record(attempt(CaseType.SLOW_QUERY, 95, List.of("databases"), 600), T0);
        progress.record(attempt(CaseType.INCIDENT, 35, List.of("incident"), 600), T0);
        assertThat(progress.suggestedNextType()).isEqualTo(CaseType.INCIDENT);

        UserProgress fresh = UserProgress.start(new UserId("u2"), T0);
        assertThat(fresh.suggestedNextType()).isNotNull();
    }
}
