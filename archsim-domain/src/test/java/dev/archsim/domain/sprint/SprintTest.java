package dev.archsim.domain.sprint;

import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SprintTest {
    private static final Instant T0 = Instant.parse("2026-09-12T14:00:00Z");
    private final UserId user = new UserId("u1");

    private Sprint sprint() {
        return Sprint.start(SprintId.of("s1"), user, 5, Duration.ofMinutes(75), Level.PLENO, null, T0);
    }

    @Test
    void startsWithNoCaseSolvedAndTimeRemaining() {
        Sprint sprint = sprint();
        assertThat(sprint.status()).isEqualTo(SprintStatus.RUNNING);
        assertThat(sprint.totalCases()).isEqualTo(5);
        assertThat(sprint.completedCases()).isZero();
        assertThat(sprint.remaining(T0)).isEqualTo(Duration.ofMinutes(75));
        assertThat(sprint.deadline()).isEqualTo(T0.plus(Duration.ofMinutes(75)));
        assertThat(sprint.expired(T0.plus(Duration.ofMinutes(74)))).isFalse();
    }

    @Test
    void recordsEachAttemptAndFinishesWhenTheLastCaseIsSubmitted() {
        Sprint sprint = sprint();
        for (int i = 0; i < 4; i++) {
            sprint.recordResult(AttemptId.of("a" + i), CaseType.SLOW_QUERY, 70 + i, T0.plusSeconds(300L * (i + 1)));
            assertThat(sprint.status()).isEqualTo(SprintStatus.RUNNING);
        }
        sprint.recordResult(AttemptId.of("a4"), CaseType.INCIDENT, 90, T0.plusSeconds(1800));

        assertThat(sprint.status()).isEqualTo(SprintStatus.FINISHED);
        assertThat(sprint.completedCases()).isEqualTo(5);
        assertThat(sprint.averageScore()).isEqualTo(75.2);   // (70+71+72+73+90)/5
        assertThat(sprint.results()).hasSize(5);
        assertThat(sprint.finishedAt()).contains(T0.plusSeconds(1800));
        assertThat(sprint.pullEvents()).singleElement().isInstanceOf(SprintFinished.class);
    }

    @Test
    void expiresWhenTheClockRunsOutAndStopsAcceptingResults() {
        Sprint sprint = sprint();
        sprint.recordResult(AttemptId.of("a0"), CaseType.ADR, 80, T0.plusSeconds(600));

        Instant late = T0.plus(Duration.ofMinutes(76));
        assertThat(sprint.expired(late)).isTrue();
        sprint.expireIfDue(late);
        assertThat(sprint.status()).isEqualTo(SprintStatus.EXPIRED);
        assertThat(sprint.remaining(late)).isZero();
        assertThat(sprint.completedCases()).isEqualTo(1);
        assertThat(sprint.pullEvents()).singleElement().isInstanceOf(SprintFinished.class);

        assertThatThrownBy(() -> sprint.recordResult(AttemptId.of("a1"), CaseType.ADR, 90, late))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abandonedSprintDoesNotEmitAFinishedEventTwice() {
        Sprint sprint = sprint();
        sprint.expireIfDue(T0.plus(Duration.ofMinutes(90)));
        sprint.expireIfDue(T0.plus(Duration.ofMinutes(91)));
        assertThat(sprint.pullEvents()).hasSize(1);
    }

    @Test
    void mixedSprintKeepsTheRequestedTypeWhenThereIsOne() {
        Sprint typed = Sprint.start(SprintId.of("s2"), user, 3, Duration.ofMinutes(30), Level.SENIOR, CaseType.INCIDENT, T0);
        assertThat(typed.caseType()).contains(CaseType.INCIDENT);
        assertThat(sprint().caseType()).isEmpty();
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> Sprint.start(SprintId.of("s"), user, 0, Duration.ofMinutes(10), Level.PLENO, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sprint.start(SprintId.of("s"), user, 5, Duration.ZERO, Level.PLENO, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultsCarryTheTypeSoTheSummaryCanBreakItDown() {
        Sprint sprint = sprint();
        sprint.recordResult(AttemptId.of("a0"), CaseType.SLOW_QUERY, 60, T0.plusSeconds(120));
        sprint.recordResult(AttemptId.of("a1"), CaseType.INCIDENT, 100, T0.plusSeconds(240));
        assertThat(sprint.results()).extracting(SprintResult::caseType).containsExactly(CaseType.SLOW_QUERY, CaseType.INCIDENT);
        assertThat(sprint.results().getFirst().elapsedSeconds()).isEqualTo(120);
        assertThat(sprint.bestResult()).map(SprintResult::score).contains(100.0);
        assertThat(sprint.worstResult()).map(SprintResult::score).contains(60.0);
    }

    @Test
    void progressShowsHowManyCasesAreLeft() {
        Sprint sprint = sprint();
        sprint.recordResult(AttemptId.of("a0"), CaseType.ADR, 70, T0.plusSeconds(100));
        assertThat(sprint.remainingCases()).isEqualTo(4);
        assertThat(List.of(sprint.completedCases(), sprint.remainingCases())).containsExactly(1, 4);
    }
}
