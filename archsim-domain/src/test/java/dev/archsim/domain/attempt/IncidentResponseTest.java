package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleActionKind;
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

class IncidentResponseTest {
    private static final Instant T0 = Instant.parse("2026-09-12T18:00:00Z");

    private static Case incidentCase() {
        Rubric rubric = new Rubric(List.of(
                new Criterion("mitigation", "Mitigação segura", 40,
                        List.of(new MitigationEvidence(null, MitigationEffect.RESOLVES, true)),
                        "Você não estancou o sangramento com uma ação segura", "Mitigou com segurança antes da correção definitiva"),
                new Criterion("communication", "Comunicação", 30,
                        List.of(new StatusUpdateEvidence(2, 900)),
                        "Faltou manter os stakeholders informados durante o incidente", "Stakeholders informados no ritmo certo"),
                new Criterion("postmortem", "Post-mortem", 30,
                        List.of(new SectionEvidence(List.of("Impacto", "Linha do tempo", "Causa raiz", "Ações"))),
                        "O post-mortem precisa de impacto, linha do tempo, causa raiz e ações", "Post-mortem completo")));
        SolutionPath path = new SolutionPath("rollback", "Rollback do deploy", "s", List.of(), List.of(), "w", rubric);
        List<Mitigation> mitigations = List.of(
                new Mitigation("rollback-deploy", "Rollback do deploy 2026.37.4", "Reverte para a versão anterior",
                        Verdict.GOOD, MitigationEffect.RESOLVES, true,
                        "Rollback com diagnóstico: o p99 volta ao normal em 4 minutos.",
                        "Você reverteu sem nenhuma evidência de que o deploy é a causa; deu certo por sorte."),
                new Mitigation("restart-pods", "Reiniciar os pods", "Recria os pods do serviço",
                        Verdict.BAD, MitigationEffect.HARMFUL, false,
                        "Restart não ajuda aqui e destrói a evidência.", "Restart não ajuda aqui e destrói a evidência."),
                new Mitigation("scale-out", "Dobrar o número de pods", "Escala horizontal",
                        Verdict.RISKY, MitigationEffect.PARTIAL, false,
                        "Alivia a fila, mas dobra a pressão no banco.", "Alivia a fila, mas dobra a pressão no banco."));
        List<StakeholderMessage> messages = List.of(
                new StakeholderMessage("cto", "Marina (CTO)", 300, "O que está acontecendo? O app está fora para os clientes?", true),
                new StakeholderMessage("support", "Rafael (Suporte)", 900, "Posso avisar os lojistas de previsão de normalização?", true));
        return new Case(CaseId.of("c-inc"), new CompanyId("banco-digital"), "t", CaseType.INCIDENT, Level.SENIOR, 7L,
                new Narrative("t", "s", "b", List.of()),
                List.of(new Checkpoint("first", "Por onde?", List.of(
                        new CheckpointOption("metrics", "Métricas", Verdict.GOOD, "boa"),
                        new CheckpointOption("restart", "Reiniciar", Verdict.BAD, "ruim")))),
                List.of(path), new AnswerForm(List.of(), "solutionPath"), new ReferenceAnswer("ref"),
                new ScenarioSpec("incident", Map.of("onsetAt", T0.minusSeconds(1800).toString())),
                new PathRecommendation("rollback", "porque o deploy às 17:32 é a única mudança correlacionada"),
                List.of("incident"), Set.of(ConsoleTool.LOGS), Map.of(), T0, mitigations, messages);
    }

    private final Case theCase = incidentCase();
    private final Attempt attempt = Attempt.start(AttemptId.of("a"), new UserId("u"), theCase, T0);

    @Test
    void appliesMitigationRecordsItAndReportsEffect() {
        attempt.recordConsoleAction(new ConsoleAction(ConsoleActionKind.DEPLOYS_VIEW, "account-api", "timeline", T0.plusSeconds(60)));
        MitigationOutcome outcome = attempt.applyMitigation(theCase, "rollback-deploy", T0.plusSeconds(420));

        assertThat(outcome.effect()).isEqualTo(MitigationEffect.RESOLVES);
        assertThat(outcome.verdict()).isEqualTo(Verdict.GOOD);
        assertThat(outcome.message()).contains("volta ao normal");
        assertThat(outcome.incidentResolved()).isTrue();
        assertThat(attempt.appliedMitigations()).singleElement().satisfies(m -> {
            assertThat(m.mitigationId()).isEqualTo("rollback-deploy");
            assertThat(m.informed()).isTrue();
        });
        assertThat(attempt.trail().count(ConsoleActionKind.MITIGATION)).isEqualTo(1);
        assertThat(attempt.timeToMitigateSeconds()).contains(420L);
        assertThat(attempt.resolvedAt()).contains(T0.plusSeconds(420));
    }

    @Test
    void blindMitigationIsFlaggedAsUninformed() {
        MitigationOutcome outcome = attempt.applyMitigation(theCase, "rollback-deploy", T0.plusSeconds(30));
        assertThat(outcome.message()).contains("sem nenhuma evidência");
        assertThat(attempt.appliedMitigations().getFirst().informed()).isFalse();
        assertThat(outcome.incidentResolved()).isTrue();
    }

    @Test
    void harmfulMitigationDoesNotResolveAndKeepsIncidentOpen() {
        MitigationOutcome outcome = attempt.applyMitigation(theCase, "restart-pods", T0.plusSeconds(120));
        assertThat(outcome.effect()).isEqualTo(MitigationEffect.HARMFUL);
        assertThat(outcome.incidentResolved()).isFalse();
        assertThat(attempt.resolvedAt()).isEmpty();
        assertThat(attempt.timeToMitigateSeconds()).isEmpty();

        attempt.applyMitigation(theCase, "scale-out", T0.plusSeconds(300));
        assertThat(attempt.appliedMitigations()).hasSize(2);
        assertThat(attempt.resolvedAt()).isEmpty();
    }

    @Test
    void stakeholderMessagesAppearOverTimeAndAreAnsweredByStatusUpdates() {
        assertThat(theCase.stakeholderMessagesUntil(60)).isEmpty();
        assertThat(theCase.stakeholderMessagesUntil(400)).extracting(StakeholderMessage::id).containsExactly("cto");
        assertThat(theCase.stakeholderMessagesUntil(1000)).hasSize(2);

        attempt.postStatusUpdate("Impacto: extrato fora para 100% dos clientes desde 17:35. Investigando o deploy das 17:32.", T0.plusSeconds(360));
        attempt.postStatusUpdate("Rollback aplicado às 18:07, p99 caindo. Previsão de normalização em 10 min.", T0.plusSeconds(700));

        assertThat(attempt.statusUpdates()).hasSize(2);
        assertThat(attempt.statusUpdates().getFirst().text()).startsWith("Impacto:");
        assertThat(attempt.answeredStakeholders(theCase)).containsExactly("cto");
        assertThatThrownBy(() -> attempt.postStatusUpdate("   ", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rubricScoresMitigationCommunicationAndPostMortem() {
        attempt.recordConsoleAction(new ConsoleAction(ConsoleActionKind.LOG_SEARCH, "account-api", "level=ERROR", T0.plusSeconds(60)));
        attempt.applyMitigation(theCase, "rollback-deploy", T0.plusSeconds(400));
        attempt.postStatusUpdate("Impacto e hipótese inicial.", T0.plusSeconds(300));
        attempt.postStatusUpdate("Mitigado, monitorando.", T0.plusSeconds(800));

        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of("solutionPath", "rollback")),
                "## Impacto\n100% do extrato\n## Linha do tempo\n17:32 deploy\n## Causa raiz\nN+1\n## Ações\nRollback e teste de regressão", null, null);
        Evaluation ev = new RubricEvaluator().evaluate(theCase, attempt.withFinalAnswer(answer), T0.plusSeconds(1800));

        assertThat(score(ev, "mitigation")).isEqualTo(40);
        assertThat(score(ev, "communication")).isEqualTo(30);
        assertThat(score(ev, "postmortem")).isEqualTo(30);
        assertThat(ev.investigationNotes()).anySatisfy(n -> assertThat(n).contains("Tempo até a mitigação"));
    }

    @Test
    void uninformedMitigationDoesNotSatisfyTheEvidenceThatRequiresDiagnosis() {
        attempt.applyMitigation(theCase, "rollback-deploy", T0.plusSeconds(30));
        FinalAnswer answer = new FinalAnswer(new StructuredFields(Map.of("solutionPath", "rollback")), "texto", null, null);
        Evaluation ev = new RubricEvaluator().evaluate(theCase, attempt.withFinalAnswer(answer), T0.plusSeconds(600));
        assertThat(score(ev, "mitigation")).isZero();
        assertThat(ev.gaps()).anySatisfy(g -> assertThat(g).contains("estancou"));
    }

    private static double score(Evaluation ev, String id) {
        return ev.criteria().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().score();
    }
}
