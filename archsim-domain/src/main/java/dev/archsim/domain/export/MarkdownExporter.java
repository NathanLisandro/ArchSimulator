package dev.archsim.domain.export;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.CriterionScore;
import dev.archsim.domain.attempt.Evaluation;
import dev.archsim.domain.attempt.FinalAnswer;
import dev.archsim.domain.attempt.StatusUpdate;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.FieldSpec;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Exports what the user wrote as a document they can take to work: the ADR or post-mortem itself, followed by the
 * evaluation and the reference answer. The user's text comes first and untouched — this is their document.
 */
public final class MarkdownExporter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    public String export(Case theCase, Attempt attempt) {
        FinalAnswer answer = attempt.finalAnswer()
                .orElseThrow(() -> new IllegalStateException("A tentativa ainda não foi enviada"));
        StringBuilder md = new StringBuilder();

        md.append("# ").append(titleFor(theCase)).append('\n');
        md.append("_").append(theCase.narrative().title()).append("_\n\n");
        md.append("- **Empresa:** ").append(theCase.companyId().value()).append('\n');
        md.append("- **Tipo:** ").append(label(theCase.type())).append('\n');
        md.append("- **Nível:** ").append(theCase.level().name().toLowerCase(Locale.ROOT)).append('\n');
        md.append("- **Data:** ").append(STAMP.format(attempt.startedAt())).append('\n');
        if (attempt.submittedAt() != null) md.append("- **Tempo de resolução:** ").append(humanDuration(attempt.durationSeconds())).append('\n');
        attempt.timeToMitigateSeconds().ifPresent(s -> md.append("- **Tempo até a mitigação:** ").append(humanDuration(s)).append('\n'));
        md.append('\n');

        md.append(mainDocument(theCase, answer)).append("\n\n");

        if (!answer.fields().values().isEmpty()) {
            md.append("## Decisões registradas\n\n| Campo | Valor |\n|---|---|\n");
            for (FieldSpec field : theCase.answerForm().fields()) {
                String value = answer.fields().value(field.id());
                if (value.isBlank() || isLongText(value)) continue;
                md.append("| ").append(field.label()).append(" | ").append(value).append(" |\n");
            }
            md.append('\n');
        }

        if (answer.mermaid() != null && !answer.mermaid().isBlank()) {
            md.append("## Desenho\n\n```mermaid\n").append(answer.mermaid().trim()).append("\n```\n\n");
        } else if (answer.graph() != null && !answer.graph().nodes().isEmpty()) {
            md.append("## Desenho\n\n```mermaid\n").append(toMermaid(answer)).append("```\n\n");
        }

        if (!attempt.statusUpdates().isEmpty()) {
            md.append("## Comunicação durante o incidente\n\n");
            for (StatusUpdate update : attempt.statusUpdates()) {
                md.append("- **+").append(humanDuration(update.elapsedSeconds())).append("** — ").append(update.text()).append('\n');
            }
            md.append('\n');
        }

        attempt.evaluation().ifPresent(evaluation -> md.append(evaluationSection(theCase, evaluation)));
        return md.toString();
    }

    private static String mainDocument(Case theCase, FinalAnswer answer) {
        String longest = answer.markdown();
        for (FieldSpec field : theCase.answerForm().fields()) {
            String value = answer.fields().value(field.id());
            if (value.length() > longest.length()) longest = value;
        }
        return longest.isBlank() ? "_(sem texto)_" : longest.trim();
    }

    private static String evaluationSection(Case theCase, Evaluation evaluation) {
        StringBuilder md = new StringBuilder("---\n\n## Avaliação\n\n");
        md.append("**Pontuação: ").append(Math.round(evaluation.totalScore())).append("/100**");
        theCase.solutionPath(evaluation.followedPathId())
                .ifPresent(p -> md.append(" · caminho seguido: ").append(p.name()));
        md.append("\n\n| Critério | Peso | Nota |\n|---|---:|---:|\n");
        for (CriterionScore score : evaluation.criteria()) {
            md.append("| ").append(score.name()).append(" | ").append(score.weight()).append(" | ")
                    .append(formatScore(score.score())).append(" |\n");
        }
        if (!evaluation.gaps().isEmpty()) {
            md.append("\n### O que faltou\n\n");
            evaluation.gaps().forEach(g -> md.append("- ").append(g).append('\n'));
        }
        if (!evaluation.strengths().isEmpty()) {
            md.append("\n### O que foi bem\n\n");
            evaluation.strengths().forEach(s -> md.append("- ").append(s).append('\n'));
        }
        if (evaluation.comparison() != null) {
            md.append("\n### Caminhos\n\n").append(evaluation.comparison().message()).append('\n');
        }
        if (evaluation.referenceMarkdown() != null && !evaluation.referenceMarkdown().isBlank()) {
            md.append("\n---\n\n## Resposta de referência\n\n").append(evaluation.referenceMarkdown().trim()).append('\n');
        }
        return md.toString();
    }

    private static String toMermaid(FinalAnswer answer) {
        StringBuilder md = new StringBuilder("flowchart LR\n");
        answer.graph().nodes().forEach(n -> md.append("  ").append(sanitize(n.id())).append("[\"").append(n.label()).append("\"]\n"));
        answer.graph().edges().forEach(e -> md.append("  ").append(sanitize(e.from()))
                .append(e.async() ? " -.->" : " -->")
                .append(e.label() == null || e.label().isBlank() ? "" : "|" + e.label() + "|")
                .append(' ').append(sanitize(e.to())).append('\n'));
        return md.toString();
    }

    private static String sanitize(String id) { return id.replaceAll("[^A-Za-z0-9_]", "_"); }

    private static boolean isLongText(String value) { return value.length() > 120 || value.contains("\n"); }

    private static String formatScore(double score) {
        return score == Math.rint(score) ? String.valueOf((long) score) : String.valueOf(score);
    }

    private static String titleFor(Case theCase) {
        return switch (theCase.type()) {
            case ADR -> "ADR";
            case INCIDENT -> "Post-mortem";
            case SOLUTION_DESIGN -> "Design de solução";
            case TRADE_OFF -> "Decisão de trade-off";
            case CODE_REVIEW -> "Revisão de código";
            case CAPACITY_PLANNING -> "Plano de capacidade";
            case MIGRATION -> "Plano de migração";
            case SLOW_QUERY -> "Investigação de performance";
        };
    }

    private static String label(CaseType type) {
        return switch (type) {
            case SLOW_QUERY -> "Query lenta";
            case INCIDENT -> "Incidente em produção";
            case ADR -> "Decisão arquitetural (ADR)";
            case SOLUTION_DESIGN -> "Design de solução";
            case TRADE_OFF -> "Trade-off forçado";
            case CODE_REVIEW -> "Code review";
            case CAPACITY_PLANNING -> "Capacity planning";
            case MIGRATION -> "Migração/modernização";
        };
    }

    /** "1h 20min", "12min 30s", "45s". */
    static String humanDuration(long seconds) {
        Duration d = Duration.ofSeconds(Math.max(0, seconds));
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long secs = d.toSecondsPart();
        if (hours > 0) return hours + "h" + (minutes > 0 ? " " + minutes + "min" : "");
        if (minutes > 0) return minutes + "min" + (secs > 0 ? " " + secs + "s" : "");
        return secs + "s";
    }

    /** Suggested file name for the download. */
    public String fileName(Case theCase, Attempt attempt) {
        String prefix = switch (theCase.type()) {
            case ADR -> "adr";
            case INCIDENT -> "post-mortem";
            default -> theCase.type().name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
        return prefix + "-" + theCase.companyId().value() + "-" + attempt.id().value().substring(0, 8) + ".md";
    }
}
