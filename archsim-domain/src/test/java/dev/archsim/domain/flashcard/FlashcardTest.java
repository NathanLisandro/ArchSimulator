package dev.archsim.domain.flashcard;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.Rubric;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlashcardTest {
    private static final Instant DAY0 = Instant.parse("2026-09-12T09:00:00Z");
    private final UserId user = new UserId("u1");

    // ---------------------------------------------------------------- SM-2

    @Test
    void newCardIsDueImmediatelyAndGraduatesThroughTheFirstIntervals() {
        ReviewSchedule schedule = ReviewSchedule.newCard(user, new FlashcardId("c1"), DAY0);
        assertThat(schedule.isDue(DAY0)).isTrue();
        assertThat(schedule.repetitions()).isZero();
        assertThat(schedule.easeFactor()).isEqualTo(2.5);

        schedule = schedule.review(ReviewGrade.GOOD, DAY0);
        assertThat(schedule.intervalDays()).isEqualTo(1);
        assertThat(schedule.dueAt()).isEqualTo(DAY0.plus(Duration.ofDays(1)));
        assertThat(schedule.isDue(DAY0.plus(Duration.ofHours(23)))).isFalse();

        schedule = schedule.review(ReviewGrade.GOOD, schedule.dueAt());
        assertThat(schedule.intervalDays()).isEqualTo(6);

        schedule = schedule.review(ReviewGrade.GOOD, schedule.dueAt());
        assertThat(schedule.intervalDays()).isEqualTo(15);
        assertThat(schedule.repetitions()).isEqualTo(3);
    }

    @Test
    void easyGradeStretchesAndHardGradeShortensTheInterval() {
        ReviewSchedule base = ReviewSchedule.newCard(user, new FlashcardId("c1"), DAY0)
                .review(ReviewGrade.GOOD, DAY0).review(ReviewGrade.GOOD, DAY0.plus(Duration.ofDays(1)));

        ReviewSchedule easy = base.review(ReviewGrade.EASY, base.dueAt());
        ReviewSchedule hard = base.review(ReviewGrade.HARD, base.dueAt());
        assertThat(easy.intervalDays()).isGreaterThan(hard.intervalDays());
        assertThat(easy.easeFactor()).isGreaterThan(hard.easeFactor());
        assertThat(hard.easeFactor()).isGreaterThanOrEqualTo(1.3);
    }

    @Test
    void forgettingResetsTheIntervalButKeepsTheEaseFloor() {
        ReviewSchedule schedule = ReviewSchedule.newCard(user, new FlashcardId("c1"), DAY0);
        for (int i = 0; i < 5; i++) schedule = schedule.review(ReviewGrade.GOOD, schedule.dueAt());
        assertThat(schedule.intervalDays()).isGreaterThan(30);

        ReviewSchedule forgotten = schedule.review(ReviewGrade.AGAIN, schedule.dueAt());
        assertThat(forgotten.repetitions()).isZero();
        assertThat(forgotten.intervalDays()).isEqualTo(1);
        assertThat(forgotten.lapses()).isEqualTo(1);
        assertThat(forgotten.easeFactor()).isLessThan(schedule.easeFactor()).isGreaterThanOrEqualTo(1.3);

        ReviewSchedule stubborn = forgotten;
        for (int i = 0; i < 12; i++) stubborn = stubborn.review(ReviewGrade.AGAIN, stubborn.dueAt());
        assertThat(stubborn.easeFactor()).isEqualTo(1.3);
    }

    // ---------------------------------------------------------------- deck

    @Test
    void deckReturnsDueCardsOldestFirstRespectingTheDailyLimit() {
        List<Flashcard> cards = List.of(
                card("c1", "consistencia"), card("c2", "consistencia"), card("c3", "kafka"));
        Deck deck = new Deck("consistencia", "Consistência", "Transações e consistência", cards);

        ReviewSession session = new ReviewSession(List.of(
                ReviewSchedule.newCard(user, new FlashcardId("c1"), DAY0).review(ReviewGrade.GOOD, DAY0),
                ReviewSchedule.newCard(user, new FlashcardId("c2"), DAY0),
                ReviewSchedule.newCard(user, new FlashcardId("c3"), DAY0)));

        List<FlashcardId> due = session.dueCards(DAY0.plus(Duration.ofDays(2)), 10);
        assertThat(due).containsExactly(new FlashcardId("c2"), new FlashcardId("c3"), new FlashcardId("c1"));
        assertThat(session.dueCards(DAY0.plus(Duration.ofDays(2)), 2)).hasSize(2);
        assertThat(session.dueCards(DAY0, 10)).doesNotContain(new FlashcardId("c1"));
        assertThat(deck.card("c1")).isPresent();
        assertThat(deck.cards()).hasSize(3);
    }

    // ---------------------------------------------------------------- generation from solved cases

    @Test
    void generatesCardsFromCheckpointsAndTradeOffsOfASolvedCase() {
        Case solved = caseForCards();
        Attempt attempt = Attempt.start(AttemptId.of("a"), user, solved, DAY0);
        attempt.answerCheckpoint(solved, "first-tool", "logs", true, DAY0);

        List<Flashcard> cards = new FlashcardFactory().fromSolvedCase(solved, attempt);

        assertThat(cards).isNotEmpty();
        assertThat(cards).allSatisfy(c -> {
            assertThat(c.front()).isNotBlank();
            assertThat(c.back()).isNotBlank();
            assertThat(c.deckId()).isNotBlank();
            assertThat(c.origin()).contains(solved.id().value());
        });
        assertThat(cards).anySatisfy(c -> assertThat(c.front()).contains("Por onde"));
        assertThat(cards).anySatisfy(c -> assertThat(c.front()).contains("Quando preferir"));
        assertThat(cards).anySatisfy(c -> assertThat(c.back()).contains("80M"));
        assertThat(cards.stream().map(Flashcard::id).distinct()).hasSameSizeAs(cards);

        // a mistaken checkpoint is worth remembering; a correct one is not
        assertThat(cards).anySatisfy(c -> assertThat(c.back()).contains("ruim: 80M linhas"));
    }

    @Test
    void generationIsStableAcrossCallsSoRepeatedCasesDoNotDuplicateCards() {
        Case solved = caseForCards();
        Attempt attempt = Attempt.start(AttemptId.of("a"), user, solved, DAY0);
        FlashcardFactory factory = new FlashcardFactory();
        assertThat(factory.fromSolvedCase(solved, attempt)).isEqualTo(factory.fromSolvedCase(solved, attempt));
    }

    @Test
    void cardRequiresBothSides() {
        assertThatThrownBy(() -> new Flashcard(new FlashcardId("x"), "deck", "", "verso", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flashcard(new FlashcardId("x"), "deck", "frente", " ", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Flashcard card(String id, String deck) {
        return new Flashcard(new FlashcardId(id), deck, "frente " + id, "verso " + id, List.of("eda"), null);
    }

    private static Case caseForCards() {
        SolutionPath outbox = new SolutionPath("outbox", "Outbox", "Publica pela mesma transação",
                List.of("uma transação local"), List.of("entrega ao menos uma vez"),
                "quando o consumidor pode ser idempotente e a latência importa", new Rubric(List.of()));
        SolutionPath twoPc = new SolutionPath("2pc", "Commit em duas fases", "Coordena banco e broker",
                List.of("atomicidade aparente"), List.of("transações in-doubt"),
                "quando os participantes têm XA maduro e o volume é baixo", new Rubric(List.of()));
        Checkpoint cp = new Checkpoint("first-tool", "Por onde você começa a investigação?", List.of(
                new CheckpointOption("metrics", "Métricas por serviço", Verdict.GOOD, "boa: isola o escopo"),
                new CheckpointOption("logs", "Logs do banco", Verdict.BAD, "ruim: 80M linhas de ledger não cabem em leitura manual")));
        return new Case(CaseId.of("case-77"), new CompanyId("banco-digital"), "t", CaseType.TRADE_OFF, Level.SENIOR, 1L,
                new Narrative("Outbox ou 2PC", "s", "b", List.of()), List.of(cp), List.of(outbox, twoPc),
                new AnswerForm(List.of(), "solutionPath"), new ReferenceAnswer("ref"), new ScenarioSpec("none", Map.of()),
                new PathRecommendation("outbox", "porque a latência do PIX é requisito"), List.of("eda", "microservices"),
                Set.of(ConsoleTool.LOGS), Map.of(), DAY0);
    }
}
