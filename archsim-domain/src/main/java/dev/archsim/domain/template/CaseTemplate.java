package dev.archsim.domain.template;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate root loaded from content YAML. All texts may contain {{param}} placeholders; the CaseGenerator renders them.
 * parameters/derived are ordered maps: derived expressions may reference earlier parameters.
 */
public record CaseTemplate(String id, CaseType type, CompanyId companyId, List<Level> levels, List<String> competencies,
                           Map<String, ParameterSpec> parameters, Map<String, String> derived,
                           Narrative narrative, List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm,
                           String referenceMarkdown, ScenarioSpec scenario, String defaultPathId, List<RecommendationRule> rules,
                           Set<ConsoleTool> tools, List<Mitigation> mitigations, List<StakeholderMessage> stakeholderMessages,
                           List<CaseArtifact> artifacts) {

    /** Templates with incident response but no artifacts. */
    public CaseTemplate(String id, CaseType type, CompanyId companyId, List<Level> levels, List<String> competencies,
                        Map<String, ParameterSpec> parameters, Map<String, String> derived, Narrative narrative,
                        List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm,
                        String referenceMarkdown, ScenarioSpec scenario, String defaultPathId, List<RecommendationRule> rules,
                        Set<ConsoleTool> tools, List<Mitigation> mitigations, List<StakeholderMessage> stakeholderMessages) {
        this(id, type, companyId, levels, competencies, parameters, derived, narrative, checkpoints, solutionPaths, answerForm,
                referenceMarkdown, scenario, defaultPathId, rules, tools, mitigations, stakeholderMessages, List.of());
    }

    /** Templates without incident response declare neither mitigations nor stakeholder pressure. */
    public CaseTemplate(String id, CaseType type, CompanyId companyId, List<Level> levels, List<String> competencies,
                        Map<String, ParameterSpec> parameters, Map<String, String> derived, Narrative narrative,
                        List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm,
                        String referenceMarkdown, ScenarioSpec scenario, String defaultPathId, List<RecommendationRule> rules,
                        Set<ConsoleTool> tools) {
        this(id, type, companyId, levels, competencies, parameters, derived, narrative, checkpoints, solutionPaths, answerForm,
                referenceMarkdown, scenario, defaultPathId, rules, tools, List.of(), List.of(), List.of());
    }

    public boolean supports(Level level) { return levels.contains(level); }
}
