package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.evaluation.CaseEvaluator;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.fixtures.Fixtures;
import dev.archsim.domain.cases.Verdict;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptTest {
    private final Instant t0 = Instant.parse("2026-09-12T15:00:00Z");
    private final Case theCase = new CaseGenerator().generate(Fixtures.slowQueryTemplate(), Level.JUNIOR, new SeededRandom(3L), t0);
    private final Attempt attempt = Attempt.start(AttemptId.of("a1"), new UserId("u1"), theCase, t0);

    @Test
    void goodCheckpointChoiceConfirmsWithoutConfirmationStep() {
        DirectionalFeedback fb = attempt.answerCheckpoint(theCase, "first-tool", "metrics", false, t0);
        assertThat(fb.verdict()).isEqualTo(Verdict.GOOD);
        assertThat(fb.requiresConfirmation()).isFalse();
        assertThat(fb.message()).contains("Boa");
        assertThat(attempt.checkpointAnswers()).hasSize(1);
        assertThat(attempt.checkpointAnswers().getFirst().proceededDespiteWarning()).isFalse();
    }

    @Test
    void badChoiceWarnsThenUserMayReviseOrProceed() {
        DirectionalFeedback fb = attempt.answerCheckpoint(theCase, "first-tool", "logs-db", false, t0);
        assertThat(fb.verdict()).isEqualTo(Verdict.BAD);
        assertThat(fb.requiresConfirmation()).isTrue();
        assertThat(attempt.checkpointAnswers().getFirst().confirmed()).isFalse();

        attempt.answerCheckpoint(theCase, "first-tool", "logs-db", true, t0.plusSeconds(10));
        CheckpointAnswer answer = attempt.checkpointAnswers().getFirst();
        assertThat(answer.confirmed()).isTrue();
        assertThat(answer.proceededDespiteWarning()).isTrue();

        attempt.answerCheckpoint(theCase, "first-tool", "metrics", false, t0.plusSeconds(20));
        answer = attempt.checkpointAnswers().getFirst();
        assertThat(attempt.checkpointAnswers()).hasSize(1);
        assertThat(answer.optionId()).isEqualTo("metrics");
        assertThat(answer.revisions()).isEqualTo(2);
        assertThat(answer.proceededDespiteWarning()).isFalse();
    }

    @Test
    void recordsInvestigationTrailAndSubmitsOnce() {
        attempt.recordConsoleAction(new ConsoleAction(ConsoleActionKind.SQL_QUERY, "pg_stat_statements", "select ...", t0));
        attempt.recordConsoleAction(new ConsoleAction(ConsoleActionKind.EXPLAIN, "ledger_entries", "explain select ...", t0));
        assertThat(attempt.trail().count(ConsoleActionKind.EXPLAIN)).isEqualTo(1);
        assertThat(attempt.trail().count(ConsoleActionKind.SQL_QUERY, "pg_stat")).isEqualTo(1);

        CaseEvaluator stub = (c, a, at) -> new Evaluation(72.5, "composite-index", List.of(), List.of("ok"), List.of(), null, "ref", List.of(), at);
        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of("solutionPath", "composite-index")), "## Causa\ntexto", null, null);
        Evaluation ev = attempt.submit(theCase, answer, stub, t0.plusSeconds(600));

        assertThat(ev.totalScore()).isEqualTo(72.5);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.durationSeconds()).isEqualTo(600);
        assertThat(attempt.pullEvents()).singleElement().isInstanceOf(AttemptEvaluated.class);
        assertThat(attempt.pullEvents()).isEmpty();
        assertThatThrownBy(() -> attempt.submit(theCase, answer, stub, t0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.answerCheckpoint(theCase, "first-tool", "metrics", false, t0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownCheckpointOrOption() {
        assertThatThrownBy(() -> attempt.answerCheckpoint(theCase, "nope", "metrics", false, t0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt.answerCheckpoint(theCase, "first-tool", "nope", false, t0)).isInstanceOf(IllegalArgumentException.class);
    }
}
