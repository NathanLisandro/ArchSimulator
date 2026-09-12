package dev.archsim.domain.evaluation;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.SolutionPath;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.rubric.AnswerContext;
import dev.archsim.domain.rubric.Criterion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Offline evaluator: detects the SolutionPath the user followed, scores the answer against that path's rubric,
 * adds an automatic "checkpoint judgment" criterion and explains how the choice compares with the recommended path.
 */
public final class RubricEvaluator implements CaseEvaluator {
    static final int CHECKPOINT_WEIGHT = 10;
    static final int TOO_MANY_ACTIONS = 25;

    @Override
    public Evaluation evaluate(Case theCase, Attempt attempt, Instant now) {
        AnswerContext ctx = new AttemptAnswerContext(attempt, theCase.answerForm());
        List<String> gaps = new ArrayList<>();
        List<String> strengths = new ArrayList<>();

        Optional<SolutionPath> declared = theCase.solutionPath(ctx.text(theCase.answerForm().pathFieldId()));
        SolutionPath followed = declared.orElseGet(theCase::recommendedPath);
        if (declared.isEmpty()) gaps.add("Você não declarou qual caminho de solução seguiu; a avaliação usou o caminho recomendado (" + followed.name() + ").");

        List<CriterionScore> scores = new ArrayList<>();
        for (Criterion c : followed.rubric().criteria()) {
            double fraction = c.fractionMatched(ctx);
            int total = c.evidence().size();
            int matched = (int) Math.round(fraction * total);
            double score = round(c.weight() * fraction);
            String feedback = fraction >= 1.0 ? c.feedbackWhenPresent() : c.feedbackWhenMissing();
            scores.add(new CriterionScore(c.id(), c.name(), c.weight(), score, matched, total, feedback));
            if (fraction >= 1.0) strengths.add(c.feedbackWhenPresent()); else gaps.add(c.feedbackWhenMissing());
        }
        scores.add(checkpointJudgment(theCase, attempt));

        double weight = scores.stream().mapToInt(CriterionScore::weight).sum();
        double total = weight == 0 ? 0 : round(scores.stream().mapToDouble(CriterionScore::score).sum() * 100.0 / weight);

        return new Evaluation(total, followed.id(), scores, strengths, gaps, compare(theCase, followed),
                theCase.referenceAnswer().markdown(), investigationNotes(theCase, attempt), now);
    }

    private CriterionScore checkpointJudgment(Case theCase, Attempt attempt) {
        List<CheckpointAnswer> answers = attempt.checkpointAnswers();
        int totalCheckpoints = theCase.checkpoints().size();
        double sum = answers.stream().mapToDouble(a -> a.verdict().judgmentScore()).sum();
        double fraction = totalCheckpoints == 0 ? 1.0 : sum / totalCheckpoints;
        String feedback = fraction >= 1.0 ? "Escolhas certas em todos os checkpoints."
                : answers.size() < totalCheckpoints ? "Você pulou " + (totalCheckpoints - answers.size()) + " checkpoint(s); eles orientam a investigação."
                : "Alguns checkpoints apontaram para caminhos ruins neste cenário; releia os cards de feedback.";
        return new CriterionScore("checkpoints", "Julgamento nos checkpoints", CHECKPOINT_WEIGHT, round(CHECKPOINT_WEIGHT * fraction),
                (int) Math.round(sum), totalCheckpoints, feedback);
    }

    private PathComparison compare(Case theCase, SolutionPath followed) {
        SolutionPath recommended = theCase.recommendedPath();
        List<SolutionPath> others = theCase.solutionPaths().stream().filter(p -> !p.id().equals(followed.id())).toList();
        String message;
        if (followed.id().equals(recommended.id())) {
            String alternatives = others.isEmpty() ? "" : " " + join(others.stream().map(SolutionPath::name).toList()) + (others.size() == 1 ? " também funcionaria" : " também funcionariam")
                    + ", mas com o custo de: " + join(others.stream().map(p -> p.name() + " → " + String.join("; ", p.cons())).toList()) + ".";
            message = followed.name() + " é a melhor escolha aqui: " + theCase.recommendation().reason() + "." + alternatives;
        } else {
            message = "Você seguiu " + followed.name() + ", que é um caminho válido (indicado quando: " + followed.bestWhen() + "). "
                    + "Neste contexto, " + recommended.name() + " seria mais indicado porque " + theCase.recommendation().reason() + ".";
        }
        List<PathSummary> alternatives = others.stream().map(p -> new PathSummary(p.id(), p.name(), p.bestWhen(), p.pros(), p.cons())).toList();
        return new PathComparison(followed.id(), recommended.id(), message, alternatives);
    }

    private List<String> investigationNotes(Case theCase, Attempt attempt) {
        List<String> notes = new ArrayList<>();
        InvestigationTrail trail = attempt.trail();
        if (!theCase.tools().isEmpty()) {
            notes.add("Você executou " + trail.size() + " ação(ões) no console: "
                    + summarize(trail) + ".");
            if (trail.size() > TOO_MANY_ACTIONS) notes.add("Foram muitas consultas; formule uma hipótese antes de cada uma para convergir mais rápido.");
            if (trail.size() == 0) notes.add("Você não usou o console: a conclusão veio sem evidência do ambiente.");
        }
        attempt.timeToMitigateSeconds().ifPresent(seconds ->
                notes.add("Tempo até a mitigação (MTTM): " + (seconds / 60) + "min" + (seconds % 60 == 0 ? "" : " " + (seconds % 60) + "s") + "."));
        if (!attempt.appliedMitigations().isEmpty() && attempt.resolvedAt().isEmpty())
            notes.add("Você aplicou " + attempt.appliedMitigations().size() + " ação(ões) de mitigação, mas o incidente seguiu aberto até o fim da tentativa.");
        attempt.appliedMitigations().stream().filter(m -> !m.informed()).forEach(m ->
                notes.add("A ação \"" + m.mitigationId() + "\" foi aplicada antes de qualquer investigação."));
        if (!theCase.stakeholderMessages().isEmpty()) {
            int answered = attempt.answeredStakeholders(theCase).size();
            long expecting = theCase.stakeholderMessages().stream().filter(dev.archsim.domain.cases.StakeholderMessage::expectsUpdate).count();
            notes.add("Você respondeu " + answered + " de " + expecting + " cobrança(s) de stakeholder com status update.");
        }
        attempt.checkpointAnswers().stream().filter(CheckpointAnswer::proceededDespiteWarning).forEach(a ->
                notes.add("No checkpoint \"" + theCase.checkpoint(a.checkpointId()).question() + "\" você seguiu mesmo assim após o alerta (opção: "
                        + theCase.checkpoint(a.checkpointId()).option(a.optionId()).map(o -> o.label()).orElse(a.optionId()) + ")."));
        return notes;
    }

    private static String summarize(InvestigationTrail trail) {
        List<String> parts = new ArrayList<>();
        for (ConsoleActionKind kind : ConsoleActionKind.values()) {
            int n = trail.count(kind);
            if (n > 0) parts.add(n + " " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return parts.isEmpty() ? "nenhuma" : String.join(", ", parts);
    }

    private static String join(List<String> items) { return String.join(", ", items); }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
