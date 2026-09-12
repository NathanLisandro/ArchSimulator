package dev.archsim.application.usecase;

import dev.archsim.application.dto.*;
import dev.archsim.application.fakes.InMemory;
import dev.archsim.application.fakes.TestTemplates;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.export.MarkdownExporter;
import dev.archsim.domain.flashcard.*;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressAndStudyTest {
    private final InMemory.Companies companies = new InMemory.Companies(TestTemplates.bank());
    private final InMemory.Templates templates = new InMemory.Templates(
            TestTemplates.slowQuery(Level.JUNIOR, Level.PLENO), TestTemplates.incident(Level.JUNIOR, Level.PLENO, Level.SENIOR));
    private final InMemory.Cases cases = new InMemory.Cases();
    private final InMemory.Attempts attempts = new InMemory.Attempts();
    private final InMemory.Progresses progresses = new InMemory.Progresses();
    private final InMemory.Sprints sprints = new InMemory.Sprints();
    private final InMemory.Decks decks = new InMemory.Decks();
    private final InMemory.Schedules schedules = new InMemory.Schedules();
    private final InMemory.GeneratedCards generated = new InMemory.GeneratedCards();
    private final InMemory.FixedClock clock = new InMemory.FixedClock(Instant.parse("2026-09-12T12:00:00Z"));
    private final InMemory.Events events = new InMemory.Events();
    private final UserId user = new UserId("u1");

    private final CompleteAttempt completeAttempt = new CompleteAttempt(progresses, sprints, generated, new FlashcardFactory(), events);
    private final StartCase startCase = new StartCase(companies, templates, cases, attempts, new CaseGenerator(), new InMemory.Seeds(), clock);
    private final SubmitAnswer submitAnswer = new SubmitAnswer(attempts, cases, new RubricEvaluator(), events, clock, completeAttempt);
    private final GetProgress getProgress = new GetProgress(progresses, attempts, cases, clock);
    private final ListAttempts listAttempts = new ListAttempts(attempts, cases);
    private final StartSprint startSprint = new StartSprint(sprints, clock);
    private final GetSprint getSprint = new GetSprint(sprints, clock);
    private final StudyDecks studyDecks = new StudyDecks(decks, generated, schedules, clock);
    private final ExportAttempt exportAttempt = new ExportAttempt(attempts, cases, new MarkdownExporter());

    private String solve(String caseType, Map<String, String> fields, String markdown) {
        String id = startCase.execute(new StartCase.Command(user, "banco-digital", caseType, Level.PLENO)).id();
        clock.now = clock.now.plusSeconds(600);
        submitAnswer.execute(new SubmitAnswer.Command(user, id, new AnswerInput(fields, markdown, null, null)));
        return id;
    }

    private String solveSlowQueryWell() {
        return solve("SLOW_QUERY", Map.of("rootCause", "MISSING_INDEX", "solutionPath", "composite-index"), "## Causa\nseq scan");
    }

    @Test
    void submittingAnAttemptFeedsProgressHistoryAndFlashcards() {
        solveSlowQueryWell();

        ProgressView progress = getProgress.execute(user);
        assertThat(progress.rank()).isEqualTo("JUNIOR");
        assertThat(progress.solvedCount()).isEqualTo(1);
        assertThat(progress.averageScore()).isGreaterThan(0);
        assertThat(progress.byType()).containsKey("SLOW_QUERY");
        assertThat(progress.missingForNextRank()).contains("pleno");
        assertThat(progress.suggestedNextType()).isNotBlank();

        List<AttemptSummaryView> history = listAttempts.execute(user);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().caseTitle()).isNotBlank();
        assertThat(history.getFirst().score()).isNotNull();
        assertThat(history.getFirst().status()).isEqualTo("SUBMITTED");

        assertThat(generated.findByUser(user)).isNotEmpty();
        assertThat(generated.findByUser(user)).allSatisfy(c -> assertThat(c.origin()).isNotNull());
    }

    @Test
    void progressExposesWeakAndStrongCompetenciesForTheChart() {
        solveSlowQueryWell();
        solve("INCIDENT", Map.of("solutionPath", "rollback"), "sem seções");

        ProgressView progress = getProgress.execute(user);
        assertThat(progress.solvedCount()).isEqualTo(2);
        assertThat(progress.byCompetency()).isNotEmpty();
        assertThat(progress.weakest()).isNotEmpty();
        assertThat(progress.strongest()).isNotEmpty();
        assertThat(progress.weakest()).isNotEqualTo(progress.strongest());
    }

    @Test
    void sprintRunsFiveCasesAgainstTheClockAndSummarisesAtTheEnd() {
        SprintView sprint = startSprint.execute(new StartSprint.Command(user, 2, Duration.ofMinutes(30), Level.PLENO, null));
        assertThat(sprint.status()).isEqualTo("RUNNING");
        assertThat(sprint.remainingSeconds()).isEqualTo(1800);
        assertThat(sprint.totalCases()).isEqualTo(2);

        solveSlowQueryWell();
        assertThat(getSprint.execute(user, sprint.id()).completedCases()).isEqualTo(1);

        solveSlowQueryWell();
        SprintView finished = getSprint.execute(user, sprint.id());
        assertThat(finished.status()).isEqualTo("FINISHED");
        assertThat(finished.averageScore()).isGreaterThan(0);
        assertThat(finished.results()).hasSize(2);
        assertThat(finished.remainingSeconds()).isZero();
    }

    @Test
    void sprintExpiresWhenTimeRunsOut() {
        SprintView sprint = startSprint.execute(new StartSprint.Command(user, 5, Duration.ofMinutes(10), Level.PLENO, CaseType.SLOW_QUERY));
        assertThat(sprint.caseType()).isEqualTo("SLOW_QUERY");
        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThat(getSprint.execute(user, sprint.id()).status()).isEqualTo("EXPIRED");
        SprintView fresh = startSprint.execute(new StartSprint.Command(user, 5, Duration.ofMinutes(10), Level.PLENO, null));
        assertThat(fresh.status()).as("um sprint expirado não impede começar outro").isEqualTo("RUNNING");
        assertThat(fresh.id()).isNotEqualTo(sprint.id());
    }

    @Test
    void studyModeServesDueCardsAndSchedulesTheNextReview() {
        decks.add(new Deck("consistencia", "Consistência", "Transações", List.of(
                new Flashcard(new FlashcardId("seed-1"), "consistencia", "O que é outbox?", "Publicar na mesma transação.", List.of("eda"), null),
                new Flashcard(new FlashcardId("seed-2"), "consistencia", "O que é saga?", "Transação distribuída por compensação.", List.of("eda"), null))));

        List<DeckView> available = studyDecks.decks(user);
        assertThat(available).hasSize(1);
        assertThat(available.getFirst().dueCount()).isEqualTo(2);

        ReviewCardView first = studyDecks.next(user, "consistencia").orElseThrow();
        assertThat(first.front()).isNotBlank();
        assertThat(first.back()).isNotBlank();
        assertThat(first.remainingToday()).isEqualTo(2);

        ReviewCardView.Result result = studyDecks.review(user, first.cardId(), ReviewGrade.GOOD);
        assertThat(result.intervalDays()).isEqualTo(1);
        assertThat(result.dueAt()).isEqualTo(clock.now.plus(Duration.ofDays(1)));
        assertThat(studyDecks.decks(user).getFirst().dueCount()).isEqualTo(1);

        studyDecks.review(user, studyDecks.next(user, "consistencia").orElseThrow().cardId(), ReviewGrade.AGAIN);
        assertThat(studyDecks.next(user, "consistencia")).isEmpty();
        clock.now = clock.now.plus(Duration.ofDays(2));
        assertThat(studyDecks.decks(user).getFirst().dueCount()).isEqualTo(2);
    }

    @Test
    void generatedCardsJoinTheStudyDecks() {
        solveSlowQueryWell();
        List<DeckView> available = studyDecks.decks(user);
        assertThat(available).isNotEmpty();
        assertThat(available).anySatisfy(d -> assertThat(d.generatedCards()).isGreaterThan(0));
        assertThat(studyDecks.next(user, available.getFirst().id())).isPresent();
    }

    @Test
    void exportsTheSubmittedAttemptAsMarkdown() {
        String attemptId = solveSlowQueryWell();
        ExportView export = exportAttempt.execute(user, attemptId);
        assertThat(export.fileName()).endsWith(".md");
        assertThat(export.markdown()).contains("## Avaliação").contains("## Resposta de referência");
    }
}
