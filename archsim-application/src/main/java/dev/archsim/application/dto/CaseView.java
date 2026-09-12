package dev.archsim.application.dto;

import dev.archsim.domain.attempt.CheckpointAnswer;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.console.ConsoleTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the player sees: verdicts and feedback of unanswered checkpoints are hidden; reference answer only after submission. */
public record CaseView(String id, String companyId, String type, String level, Narrative narrative, List<CheckpointView> checkpoints,
                       List<PathView> solutionPaths, AnswerForm answerForm, List<String> competencies, Set<ConsoleTool> tools,
                       Map<String, String> scenarioHints, List<MitigationOptionView> mitigations,
                       List<dev.archsim.domain.cases.CaseArtifact> artifacts) {

    public record CheckpointView(String id, String question, List<OptionView> options, AnsweredView answer) {}
    public record OptionView(String id, String label) {}
    public record AnsweredView(String optionId, String verdict, String feedback, boolean confirmed, boolean proceededDespiteWarning) {}
    public record PathView(String id, String name, String summary) {}
    /** Verdict and effect are hidden: choosing the mitigation is part of the exercise. */
    public record MitigationOptionView(String id, String label, String description) {}
    public record StakeholderView(String id, String from, String text, boolean expectsUpdate, long atElapsedSeconds) {}

    public static CaseView from(Case c, List<CheckpointAnswer> answers) {
        List<CheckpointView> cps = c.checkpoints().stream().map(cp -> {
            AnsweredView answered = answers.stream().filter(a -> a.checkpointId().equals(cp.id())).findFirst()
                    .map(a -> new AnsweredView(a.optionId(), a.verdict().name(), cp.option(a.optionId()).map(CheckpointOption::feedback).orElse(""),
                            a.confirmed(), a.proceededDespiteWarning())).orElse(null);
            return new CheckpointView(cp.id(), cp.question(), cp.options().stream().map(o -> new OptionView(o.id(), o.label())).toList(), answered);
        }).toList();
        return new CaseView(c.id().value(), c.companyId().value(), c.type().name(), c.level().name(), c.narrative(), cps,
                c.solutionPaths().stream().map(p -> new PathView(p.id(), p.name(), p.summary())).toList(),
                c.answerForm(), c.competencies(), c.tools(), Map.of("kind", c.scenario().kind()),
                c.mitigations().stream().map(m -> new MitigationOptionView(m.id(), m.label(), m.description())).toList(),
                c.artifacts());
    }
}
