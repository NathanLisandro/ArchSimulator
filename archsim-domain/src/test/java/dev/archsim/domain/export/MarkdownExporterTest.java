package dev.archsim.domain.export;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownExporterTest {
    private static final Instant T0 = Instant.parse("2026-09-12T14:00:00Z");
    private final MarkdownExporter exporter = new MarkdownExporter();

    private static Case adrCase() {
        Rubric rubric = new Rubric(List.of(new Criterion("structure", "Estrutura do ADR", 40,
                List.of(new SectionEvidence(List.of("Contexto", "Decisão"))), "faltou estrutura", "estrutura ok")));
        return new Case(CaseId.of("case-1"), new CompanyId("banco-digital"), "t", CaseType.ADR, Level.SENIOR, 1L,
                new Narrative("Agenda em tempo real", "s", "b", List.of()), List.of(),
                List.of(new SolutionPath("cqrs", "Projeção por eventos", "s", List.of(), List.of(), "quando o frescor é de segundos", rubric),
                        new SolutionPath("view", "View materializada", "s", List.of(), List.of("refresh caro"), "quando minutos bastam", rubric)),
                new AnswerForm(List.of(
                        new FieldSpec("solutionPath", "Decisão", FieldType.SELECT, List.of(), true, null),
                        new FieldSpec("consistencyModel", "Consistência", FieldType.SELECT, List.of(), true, null),
                        new FieldSpec("adr", "ADR", FieldType.MARKDOWN, List.of(), true, null)), "solutionPath"),
                new ReferenceAnswer("# ADR de referência\n\nTexto completo."), new ScenarioSpec("none", Map.of()),
                new PathRecommendation("cqrs", "porque o frescor pedido é de 10 segundos"), List.of("adr"), Set.of(), Map.of(), T0);
    }

    @Test
    void exportsTheUsersDocumentFirstThenTheEvaluationAndReference() {
        Case theCase = adrCase();
        Attempt attempt = Attempt.start(AttemptId.of("11112222-3333-4444-5555-666677778888"), new UserId("u"), theCase, T0);
        String adr = "## Contexto\nA agenda é D-1.\n## Decisão\nProjeção por eventos com outbox.";
        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of(
                "solutionPath", "cqrs", "consistencyModel", "eventual", "adr", adr)), "", null, null);
        attempt.submit(theCase, answer, new RubricEvaluator(), T0.plusSeconds(1500));

        String md = exporter.export(theCase, attempt);

        assertThat(md).startsWith("# ADR\n");
        assertThat(md).contains("_Agenda em tempo real_");
        assertThat(md).contains("- **Tipo:** Decisão arquitetural (ADR)");
        assertThat(md).contains("- **Tempo de resolução:** 25min");
        assertThat(md.indexOf("## Contexto")).isLessThan(md.indexOf("## Avaliação"));
        assertThat(md).contains("Projeção por eventos com outbox.");
        assertThat(md).contains("| Decisão | cqrs |").contains("| Consistência | eventual |");
        assertThat(md).doesNotContain("| ADR |");
        assertThat(md).contains("## Avaliação").contains("**Pontuação: ");
        assertThat(md).contains("| Estrutura do ADR | 40 |");
        assertThat(md).contains("## Resposta de referência").contains("# ADR de referência");
        assertThat(exporter.fileName(theCase, attempt)).isEqualTo("adr-banco-digital-11112222.md");
    }

    @Test
    void postMortemIncludesStatusUpdatesMitigationTimeAndTheDrawnDiagram() {
        Case theCase = incidentCase();
        Attempt attempt = Attempt.start(AttemptId.of("aaaabbbb-cccc-dddd-eeee-ffff00001111"), new UserId("u"), theCase, T0);
        attempt.postStatusUpdate("Impacto mapeado, investigando o deploy.", T0.plusSeconds(300));
        attempt.applyMitigation(theCase, "rollback", T0.plusSeconds(420));
        SolutionGraph graph = new SolutionGraph(
                List.of(new GraphNode("api", "service", "account-api", Map.of()),
                        new GraphNode("q", "queue", "orders.placed", Map.of())),
                List.of(new GraphEdge("api", "q", "publica", true, Map.of())));
        attempt.submit(theCase, new FinalAnswer(new StructuredFields(Map.of("solutionPath", "rollback-then-fix")),
                "## Impacto\nExtrato fora\n## Ações\nRollback", null, graph), new RubricEvaluator(), T0.plusSeconds(1800));

        String md = exporter.export(theCase, attempt);

        assertThat(md).startsWith("# Post-mortem\n");
        assertThat(md).contains("- **Tempo até a mitigação:** 7min");
        assertThat(md).contains("## Comunicação durante o incidente").contains("**+5min** — Impacto mapeado");
        assertThat(md).contains("```mermaid").contains("api -.->|publica| q");
        assertThat(exporter.fileName(theCase, attempt)).startsWith("post-mortem-banco-digital-");
    }

    @Test
    void refusesToExportAnUnsubmittedAttempt() {
        Case theCase = adrCase();
        Attempt attempt = Attempt.start(AttemptId.of("a"), new UserId("u"), theCase, T0);
        assertThatThrownBy(() -> exporter.export(theCase, attempt)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void formatsDurationsForHumans() {
        assertThat(MarkdownExporter.humanDuration(45)).isEqualTo("45s");
        assertThat(MarkdownExporter.humanDuration(750)).isEqualTo("12min 30s");
        assertThat(MarkdownExporter.humanDuration(4800)).isEqualTo("1h 20min");
        assertThat(MarkdownExporter.humanDuration(3600)).isEqualTo("1h");
    }

    private static Case incidentCase() {
        Rubric rubric = new Rubric(List.of(new Criterion("postmortem", "Post-mortem", 100,
                List.of(new SectionEvidence(List.of("Impacto", "Ações"))), "faltou", "ok")));
        return new Case(CaseId.of("case-2"), new CompanyId("banco-digital"), "t", CaseType.INCIDENT, Level.SENIOR, 1L,
                new Narrative("Extrato fora do ar", "s", "b", List.of()), List.of(),
                List.of(new SolutionPath("rollback-then-fix", "Rollback", "s", List.of(), List.of(), "quando a mudança é recente", rubric)),
                new AnswerForm(List.of(new FieldSpec("solutionPath", "Estratégia", FieldType.SELECT, List.of(), true, null)), "solutionPath"),
                new ReferenceAnswer("referência"), new ScenarioSpec("none", Map.of()),
                new PathRecommendation("rollback-then-fix", "porque o deploy é a única mudança"), List.of("incident"),
                Set.of(ConsoleTool.LOGS), Map.of(), T0,
                List.of(new Mitigation("rollback", "Rollback", "reverte", Verdict.GOOD, MitigationEffect.RESOLVES, false, "ok", "ok")),
                List.of());
    }
}
