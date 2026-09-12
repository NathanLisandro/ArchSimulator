package dev.archsim.application.usecase;

import dev.archsim.application.ForbiddenException;
import dev.archsim.application.NotFoundException;
import dev.archsim.application.dto.*;
import dev.archsim.application.fakes.InMemory;
import dev.archsim.application.fakes.TestTemplates;
import dev.archsim.domain.attempt.AttemptEvaluated;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.sql.ExplainSimulator;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseFlowTest {
    private final InMemory.Companies companies = new InMemory.Companies(TestTemplates.bank());
    private final InMemory.Templates templates = new InMemory.Templates(TestTemplates.slowQuery(Level.JUNIOR, Level.PLENO));
    private final InMemory.Cases cases = new InMemory.Cases();
    private final InMemory.Attempts attempts = new InMemory.Attempts();
    private final InMemory.FixedClock clock = new InMemory.FixedClock(Instant.parse("2026-09-12T15:00:00Z"));
    private final InMemory.Events events = new InMemory.Events();
    private final InMemory.RecordingSandbox sandbox = new InMemory.RecordingSandbox();
    private final InMemory.Datasets datasets = new InMemory.Datasets(companies);
    private final UserId user = new UserId("u1");

    private final StartCase startCase = new StartCase(companies, templates, cases, attempts, new CaseGenerator(), new InMemory.Seeds(), clock);
    private final AnswerCheckpoint answerCheckpoint = new AnswerCheckpoint(attempts, cases, clock);
    private final RunSql runSql = new RunSql(attempts, cases, companies, datasets, sandbox, new ExplainSimulator(), clock);
    private final QueryMetrics queryMetrics = new QueryMetrics(attempts, cases, datasets, clock);
    private final SubmitAnswer submitAnswer = new SubmitAnswer(attempts, cases, new RubricEvaluator(), events, clock,
            new CompleteAttempt(new InMemory.Progresses(), new InMemory.Sprints(), new InMemory.GeneratedCards(),
                    new dev.archsim.domain.flashcard.FlashcardFactory(), events));
    private final GetAttempt getAttempt = new GetAttempt(attempts, cases, clock);

    @Test
    void startsACaseForCompanyTypeAndLevelAndHidesVerdicts() {
        AttemptView view = startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.JUNIOR));

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.theCase().type()).isEqualTo("SLOW_QUERY");
        assertThat(view.theCase().narrative().title()).startsWith("Extrato lento: ");
        assertThat(view.theCase().checkpoints().getFirst().answer()).isNull();
        assertThat(view.theCase().checkpoints().getFirst().options()).extracting(CaseView.OptionView::label).containsExactly("Métricas", "Logs");
        assertThat(cases.data).hasSize(1);
        assertThat(attempts.data).hasSize(1);
    }

    @Test
    void randomCompanyAndTypeWhenNotGiven_andFailsWhenNoTemplateMatchesLevel() {
        assertThat(startCase.execute(new StartCase.Command(user, null, null, Level.PLENO)).theCase().companyId()).isEqualTo("banco-digital");
        assertThatThrownBy(() -> startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.STAFF)))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("STAFF");
    }

    @Test
    void checkpointAnswerReturnsFeedbackAndPersists() {
        AttemptView view = startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.JUNIOR));
        var fb = answerCheckpoint.execute(new AnswerCheckpoint.Command(user, view.id(), "first-tool", "logs", false));
        assertThat(fb.verdict()).isEqualTo("BAD");
        assertThat(fb.message()).matches("ruim: \\d+ms");
        assertThat(fb.requiresConfirmation()).isTrue();
        assertThat(getAttempt.execute(user, view.id()).theCase().checkpoints().getFirst().answer().confirmed()).isFalse();
        assertThatThrownBy(() -> answerCheckpoint.execute(new AnswerCheckpoint.Command(new UserId("other"), view.id(), "first-tool", "logs", false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sqlConsoleSimulatesExplainAndRecordsTheTrail() {
        AttemptView view = startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.JUNIOR));

        SqlResult plan = runSql.execute(new RunSql.Command(user, view.id(), "EXPLAIN SELECT * FROM ledger_entries WHERE account_id = 1 ORDER BY created_at DESC LIMIT 50"));
        assertThat(plan.planText()).contains("Seq Scan on ledger_entries");
        assertThat(plan.error()).isNull();

        SqlResult rows = runSql.execute(new RunSql.Command(user, view.id(), "select * from pg_stat_statements order by mean_exec_time desc limit 5"));
        assertThat(rows.rows()).hasSize(1);
        assertThat(sandbox.executed).hasSize(1);

        SqlResult blocked = runSql.execute(new RunSql.Command(user, view.id(), "DELETE FROM ledger_entries"));
        assertThat(blocked.error()).contains("somente leitura");

        AttemptView after = getAttempt.execute(user, view.id());
        assertThat(after.trail()).extracting(a -> a.kind()).containsExactly(ConsoleActionKind.EXPLAIN, ConsoleActionKind.SQL_QUERY);
        assertThat(after.trail().getFirst().target()).isEqualTo("ledger_entries");
        assertThat(after.trail().get(1).target()).isEqualTo("pg_stat_statements");
    }

    @Test
    void metricsQueryReturnsSeriesInWindowAndRecordsView() {
        AttemptView view = startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.JUNIOR));
        MetricsView m = queryMetrics.execute(new QueryMetrics.Command(user, view.id(), "account-api", List.of("latency_p99"),
                clock.now.minusSeconds(3600), clock.now));
        assertThat(m.series()).hasSize(1);
        assertThat(m.series().getFirst().points()).hasSize(61);
        assertThat(m.services()).contains("account-api", "pix-api", "core-db");
        assertThat(getAttempt.execute(user, view.id()).trail().getFirst().kind()).isEqualTo(ConsoleActionKind.METRICS_VIEW);
        assertThat(getAttempt.execute(user, view.id()).trail().getFirst().target()).isEqualTo("account-api:latency_p99");
    }

    @Test
    void submitEvaluatesPersistsAndPublishesEvent() {
        AttemptView view = startCase.execute(new StartCase.Command(user, "banco-digital", "SLOW_QUERY", Level.JUNIOR));
        answerCheckpoint.execute(new AnswerCheckpoint.Command(user, view.id(), "first-tool", "metrics", false));
        runSql.execute(new RunSql.Command(user, view.id(), "explain select * from ledger_entries where account_id = 1"));
        clock.now = clock.now.plusSeconds(900);

        AttemptView result = submitAnswer.execute(new SubmitAnswer.Command(user, view.id(),
                new AnswerInput(Map.of("rootCause", "MISSING_INDEX", "solutionPath", "composite-index"), "## Causa\nseq scan", null, null)));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.evaluation().totalScore()).isEqualTo(100.0);
        assertThat(result.evaluation().referenceMarkdown()).startsWith("ref ");
        assertThat(result.durationSeconds()).isEqualTo(900);
        assertThat(events.published).anySatisfy(e -> assertThat(e).isInstanceOf(AttemptEvaluated.class));
        assertThatThrownBy(() -> submitAnswer.execute(new SubmitAnswer.Command(user, view.id(), new AnswerInput(Map.of(), "", null, null))))
                .isInstanceOf(IllegalStateException.class);
    }
}
