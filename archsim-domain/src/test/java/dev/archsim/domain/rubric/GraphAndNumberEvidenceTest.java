package dev.archsim.domain.rubric;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraphAndNumberEvidenceTest {
    private static final Instant T0 = Instant.parse("2026-09-12T12:00:00Z");

    private static SolutionGraph graph() {
        return new SolutionGraph(
                List.of(new GraphNode("n1", "service", "checkout-api", Map.of()),
                        new GraphNode("n2", "queue", "orders-topic", Map.of("partitions", "24")),
                        new GraphNode("n3", "service", "inventory-service", Map.of()),
                        new GraphNode("n4", "database", "orders-db", Map.of())),
                List.of(new GraphEdge("n1", "n4", "grava pedido", false, Map.of("pattern", "outbox")),
                        new GraphEdge("n1", "n2", "publica OrderPlaced", true, Map.of("idempotent", "true")),
                        new GraphEdge("n2", "n3", "consome", true, Map.of("retry", "exponential"))));
    }

    private static Case caseWith(List<Criterion> criteria) {
        Rubric rubric = new Rubric(criteria);
        SolutionPath path = new SolutionPath("event-driven", "Assíncrono com outbox", "s", List.of(), List.of(), "w", rubric);
        AnswerForm form = new AnswerForm(List.of(
                new FieldSpec("solutionPath", "Caminho", FieldType.SELECT, List.of(), true, null),
                new FieldSpec("peakTps", "TPS de pico estimado", FieldType.NUMBER, List.of(), true, null),
                new FieldSpec("design", "Desenho", FieldType.DIAGRAM, List.of(), false, null)), "solutionPath");
        return new Case(CaseId.of("c"), new CompanyId("ecommerce"), "t", CaseType.SOLUTION_DESIGN, Level.SENIOR, 1L,
                new Narrative("t", "s", "b", List.of()), List.of(), List.of(path), form, new ReferenceAnswer("ref"),
                new ScenarioSpec("none", Map.of()), new PathRecommendation("event-driven", "r"), List.of("eda"),
                Set.of(ConsoleTool.METRICS), Map.of(), T0);
    }

    private static Evaluation evaluate(Case c, FinalAnswer answer) {
        Attempt a = Attempt.start(AttemptId.of("a"), new UserId("u"), c, T0);
        return new RubricEvaluator().evaluate(c, a.withFinalAnswer(answer), T0);
    }

    private static double score(Evaluation ev, String id) {
        return ev.criteria().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().score();
    }

    @Test
    void graphEvidenceChecksNodesEdgesAndAsynchrony() {
        Case c = caseWith(List.of(
                new Criterion("queue", "Fila entre checkout e estoque", 10,
                        List.of(new GraphEvidence(GraphEvidence.Kind.NODE_OF_TYPE, "queue", null, null, 1)), "faltou fila", "fila presente"),
                new Criterion("async", "Integração assíncrona", 10,
                        List.of(new GraphEvidence(GraphEvidence.Kind.ASYNC_EDGE, null, "checkout-api", "orders-topic", 1)), "faltou assíncrono", "assíncrono ok"),
                new Criterion("sync-forbidden", "Sem chamada síncrona ao estoque", 10,
                        List.of(new GraphEvidence(GraphEvidence.Kind.NO_SYNC_EDGE, null, "checkout-api", "inventory-service", 1)), "tem chamada síncrona", "sem chamada síncrona"),
                new Criterion("outbox", "Outbox declarado", 10,
                        List.of(new GraphEvidence(GraphEvidence.Kind.EDGE_PROPERTY, "pattern=outbox", null, null, 1)), "faltou outbox", "outbox declarado"),
                new Criterion("db", "Banco no desenho", 10,
                        List.of(new GraphEvidence(GraphEvidence.Kind.NODE_OF_TYPE, "cache", null, null, 1)), "faltou cache", "cache presente")));

        Evaluation ev = evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven")), "", null, graph()));

        assertThat(score(ev, "queue")).isEqualTo(10);
        assertThat(score(ev, "async")).isEqualTo(10);
        assertThat(score(ev, "sync-forbidden")).isEqualTo(10);
        assertThat(score(ev, "outbox")).isEqualTo(10);
        assertThat(score(ev, "db")).isZero();
    }

    @Test
    void syncEdgeBetweenTheForbiddenPairFailsTheCriterion() {
        SolutionGraph withSync = new SolutionGraph(graph().nodes(),
                List.of(new GraphEdge("n1", "n3", "consulta estoque", false, Map.of())));
        Case c = caseWith(List.of(new Criterion("sync-forbidden", "Sem chamada síncrona ao estoque", 10,
                List.of(new GraphEvidence(GraphEvidence.Kind.NO_SYNC_EDGE, null, "checkout-api", "inventory-service", 1)),
                "tem chamada síncrona no caminho do checkout", "sem chamada síncrona")));

        Evaluation ev = evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven")), "", null, withSync));
        assertThat(score(ev, "sync-forbidden")).isZero();
        assertThat(ev.gaps()).anySatisfy(g -> assertThat(g).contains("síncrona"));
    }

    @Test
    void graphEvidenceFallsBackToMermaidWhenThereIsNoGraph() {
        String mermaid = """
                flowchart LR
                  checkout-api -->|publica OrderPlaced| orders-topic
                  orders-topic -.->|consome| inventory-service
                """;
        Case c = caseWith(List.of(new Criterion("async", "Integração assíncrona", 10,
                List.of(new GraphEvidence(GraphEvidence.Kind.ASYNC_EDGE, null, "checkout-api", "orders-topic", 1)),
                "faltou assíncrono", "assíncrono ok")));

        Evaluation ev = evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven")), "", mermaid, null));
        assertThat(score(ev, "async")).isEqualTo(10);
    }

    @Test
    void numberEvidenceChecksAnEstimateWithinTolerance() {
        Case c = caseWith(List.of(
                new Criterion("tps", "Estimativa de TPS", 10,
                        List.of(new NumberEvidence("peakTps", 4200, 0.25)), "estimativa fora da ordem de grandeza", "estimativa plausível"),
                new Criterion("tps-strict", "Estimativa precisa", 10,
                        List.of(new NumberEvidence("peakTps", 4200, 0.02)), "estimativa imprecisa", "estimativa precisa")));

        Evaluation ev = evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven", "peakTps", "3600")), "", null, null));
        assertThat(score(ev, "tps")).isEqualTo(10);
        assertThat(score(ev, "tps-strict")).isZero();
    }

    @Test
    void numberEvidenceAcceptsFormattedNumbersAndRejectsNonsense() {
        Case c = caseWith(List.of(new Criterion("tps", "Estimativa", 10,
                List.of(new NumberEvidence("peakTps", 4200, 0.25)), "fora", "ok")));
        assertThat(score(evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven", "peakTps", "4.100")), "", null, null)), "tps")).isEqualTo(10);
        assertThat(score(evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven", "peakTps", "muitos")), "", null, null)), "tps")).isZero();
        assertThat(score(evaluate(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "event-driven")), "", null, null)), "tps")).isZero();
    }

    @Test
    void caseCarriesArtifactsForReviewAndMigrationCases() {
        Case c = new Case(CaseId.of("c"), new CompanyId("ecommerce"), "t", CaseType.CODE_REVIEW, Level.PLENO, 1L,
                new Narrative("t", "s", "b", List.of()), List.of(),
                List.of(new SolutionPath("p", "P", "s", List.of(), List.of(), "w", new Rubric(List.of()))),
                new AnswerForm(List.of(), "solutionPath"), new ReferenceAnswer("ref"), new ScenarioSpec("none", Map.of()),
                new PathRecommendation("p", "r"), List.of("solid"), Set.of(), Map.of(), T0, List.of(), List.of(),
                List.of(new CaseArtifact("checkout-controller", "CheckoutController.java", ArtifactKind.CODE, "java", "class X {}")));

        assertThat(c.artifacts()).hasSize(1);
        assertThat(c.artifact("checkout-controller")).isPresent();
        assertThat(c.artifact("nope")).isEmpty();
    }
}
