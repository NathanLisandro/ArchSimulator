package dev.archsim.domain.evaluation;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RubricEvaluatorTest {
    private final Instant t0 = Instant.parse("2026-09-12T15:00:00Z");

    private static Case caseWith(String recommended) {
        Rubric indexRubric = new Rubric(List.of(
                new Criterion("root-cause", "Causa raiz", 30, List.of(new ChoiceEvidence("rootCause", List.of("MISSING_INDEX"))), "Não identificou a causa raiz", "Identificou a causa raiz"),
                new Criterion("index-def", "Definição do índice", 30, List.of(new RegexEvidence("proposedSql", "(?i)create\\s+index.*\\(\\s*account_id\\s*,\\s*created_at")), "Índice não cobre igualdade + range", "Índice composto correto"),
                new Criterion("rollout", "Rollout seguro", 20, List.of(new KeywordEvidence("explanation", List.of("concurrently", "online"), 1), new SectionEvidence(List.of("Riscos"))), "Faltou falar de criação online e riscos", "Rollout seguro"),
                new Criterion("investigation", "Investigação", 20, List.of(new InvestigationEvidence(ConsoleActionKind.EXPLAIN, "ledger_entries", 1), new InvestigationEvidence(ConsoleActionKind.METRICS_VIEW, null, 1)), "Não usou EXPLAIN nem métricas", "Investigação objetiva")));
        Rubric cacheRubric = new Rubric(List.of(
                new Criterion("ttl", "TTL e invalidação", 50, List.of(new KeywordEvidence("explanation", List.of("ttl", "invalida"), 2)), "Sem estratégia de invalidação", "Invalidação definida"),
                new Criterion("root-cause", "Causa raiz", 50, List.of(new ChoiceEvidence("rootCause", List.of("MISSING_INDEX"))), "Não identificou a causa", "Causa identificada")));
        SolutionPath idx = new SolutionPath("composite-index", "Índice composto", "resumo", List.of("simples"), List.of("custo de escrita"), "filtro por igualdade + range", indexRubric);
        SolutionPath cache = new SolutionPath("cache", "Cache de extrato", "resumo", List.of("alivia o banco"), List.of("dado stale", "invalidação"), "leitura repetida", cacheRubric);
        Checkpoint cp = new Checkpoint("first-tool", "Por onde?", List.of(
                new CheckpointOption("metrics", "Métricas", Verdict.GOOD, "boa"),
                new CheckpointOption("logs", "Logs", Verdict.BAD, "ruim")));
        AnswerForm form = new AnswerForm(List.of(
                new FieldSpec("rootCause", "Causa", FieldType.SELECT, List.of(), true, null),
                new FieldSpec("solutionPath", "Caminho", FieldType.SELECT, List.of(), true, null),
                new FieldSpec("proposedSql", "SQL", FieldType.SQL, List.of(), false, null),
                new FieldSpec("explanation", "Explicação", FieldType.MARKDOWN, List.of(), true, null)), "solutionPath");
        return new Case(CaseId.of("c1"), new CompanyId("banco-digital"), "t1", CaseType.SLOW_QUERY, Level.PLENO, 1L,
                new Narrative("t", "s", "b", List.of()), List.of(cp), List.of(idx, cache), form,
                new ReferenceAnswer("ref"), new ScenarioSpec("slow-query", Map.of()), new PathRecommendation(recommended, "porque a tabela tem 80M linhas"),
                List.of("databases"), Set.of(ConsoleTool.SQL), Map.of(), Instant.EPOCH);
    }

    private final RubricEvaluator evaluator = new RubricEvaluator();

    @Test
    void scoresAgainstTheRubricOfTheFollowedPathAndComparesWithRecommendation() {
        Case c = caseWith("composite-index");
        Attempt attempt = Attempt.start(AttemptId.of("a"), new UserId("u"), c, t0);
        attempt.answerCheckpoint(c, "first-tool", "metrics", false, t0);
        attempt.recordConsoleAction(new ConsoleAction(ConsoleActionKind.EXPLAIN, "ledger_entries", "explain ...", t0));
        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of(
                "rootCause", "MISSING_INDEX", "solutionPath", "composite-index",
                "proposedSql", "CREATE INDEX CONCURRENTLY idx_ledger_acc_created ON ledger_entries (account_id, created_at DESC)")),
                "## Diagnóstico\nSeq scan.\n## Riscos\nCriar com CONCURRENTLY para não travar.", null, null);

        Evaluation ev = evaluator.evaluate(c, attempt.withFinalAnswer(answer), t0);

        assertThat(ev.followedPathId()).isEqualTo("composite-index");
        assertThat(ev.criteria()).extracting(CriterionScore::id).containsExactly("root-cause", "index-def", "rollout", "investigation", "checkpoints");
        assertThat(score(ev, "root-cause")).isEqualTo(30);
        assertThat(score(ev, "index-def")).isEqualTo(30);
        assertThat(score(ev, "rollout")).isEqualTo(20);
        assertThat(score(ev, "investigation")).isEqualTo(10);   // EXPLAIN yes, metrics no
        assertThat(score(ev, "checkpoints")).isEqualTo(10);
        assertThat(ev.totalScore()).isCloseTo(100.0 * 100 / 110, org.assertj.core.data.Offset.offset(0.01));
        assertThat(ev.gaps()).contains("Não usou EXPLAIN nem métricas");
        assertThat(ev.strengths()).contains("Índice composto correto");
        assertThat(ev.comparison().message()).contains("Índice composto").contains("melhor escolha").contains("Cache de extrato");
        assertThat(ev.referenceMarkdown()).isEqualTo("ref");
    }

    @Test
    void followingAValidButNotRecommendedPathIsNotPenalisedButExplained() {
        Case c = caseWith("composite-index");
        Attempt attempt = Attempt.start(AttemptId.of("a"), new UserId("u"), c, t0);
        attempt.answerCheckpoint(c, "first-tool", "logs", true, t0);
        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of("rootCause", "MISSING_INDEX", "solutionPath", "cache")),
                "Cache com TTL de 30s e invalidação por evento de lançamento.", null, null);

        Evaluation ev = evaluator.evaluate(c, attempt.withFinalAnswer(answer), t0);

        assertThat(ev.followedPathId()).isEqualTo("cache");
        assertThat(score(ev, "ttl")).isEqualTo(50);
        assertThat(score(ev, "checkpoints")).isEqualTo(0);
        assertThat(ev.comparison().message()).contains("Você seguiu Cache de extrato").contains("válido").contains("Índice composto").contains("80M linhas");
        assertThat(ev.investigationNotes()).anySatisfy(n -> assertThat(n).contains("mesmo assim"));
    }

    @Test
    void missingPathFallsBackToRecommendedWithAGap() {
        Case c = caseWith("cache");
        Attempt attempt = Attempt.start(AttemptId.of("a"), new UserId("u"), c, t0);
        Evaluation ev = evaluator.evaluate(c, attempt.withFinalAnswer(new FinalAnswer(new StructuredFields(Map.of()), "", null, null)), t0);
        assertThat(ev.followedPathId()).isEqualTo("cache");
        assertThat(ev.gaps()).anySatisfy(g -> assertThat(g).contains("caminho"));
        assertThat(ev.totalScore()).isZero();
    }

    private static double score(Evaluation ev, String id) {
        return ev.criteria().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().score();
    }
}
