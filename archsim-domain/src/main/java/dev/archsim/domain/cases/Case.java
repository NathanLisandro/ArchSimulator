package dev.archsim.domain.cases;

import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Aggregate root: a generated, fully rendered case instance. Immutable once generated. */
public record Case(CaseId id, CompanyId companyId, String templateId, CaseType type, Level level, long seed,
                   Narrative narrative, List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm,
                   ReferenceAnswer referenceAnswer, ScenarioSpec scenario, PathRecommendation recommendation,
                   List<String> competencies, Set<ConsoleTool> tools, Map<String, String> params, Instant generatedAt,
                   List<Mitigation> mitigations, List<StakeholderMessage> stakeholderMessages, List<CaseArtifact> artifacts,
                   List<FollowUp> followUps) {

    /** Cases without second-order follow-up. */
    public Case(CaseId id, CompanyId companyId, String templateId, CaseType type, Level level, long seed, Narrative narrative,
                List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm, ReferenceAnswer referenceAnswer,
                ScenarioSpec scenario, PathRecommendation recommendation, List<String> competencies, Set<ConsoleTool> tools,
                Map<String, String> params, Instant generatedAt, List<Mitigation> mitigations,
                List<StakeholderMessage> stakeholderMessages, List<CaseArtifact> artifacts) {
        this(id, companyId, templateId, type, level, seed, narrative, checkpoints, solutionPaths, answerForm, referenceAnswer,
                scenario, recommendation, competencies, tools, params, generatedAt, mitigations, stakeholderMessages, artifacts, List.of());
    }

    /** Incident cases without artifacts. */
    public Case(CaseId id, CompanyId companyId, String templateId, CaseType type, Level level, long seed, Narrative narrative,
                List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm, ReferenceAnswer referenceAnswer,
                ScenarioSpec scenario, PathRecommendation recommendation, List<String> competencies, Set<ConsoleTool> tools,
                Map<String, String> params, Instant generatedAt, List<Mitigation> mitigations, List<StakeholderMessage> stakeholderMessages) {
        this(id, companyId, templateId, type, level, seed, narrative, checkpoints, solutionPaths, answerForm, referenceAnswer,
                scenario, recommendation, competencies, tools, params, generatedAt, mitigations, stakeholderMessages, List.of(), List.of());
    }

    /** Cases without incident response (slow query, ADR, design...) have no mitigations nor stakeholder pressure. */
    public Case(CaseId id, CompanyId companyId, String templateId, CaseType type, Level level, long seed, Narrative narrative,
                List<Checkpoint> checkpoints, List<SolutionPath> solutionPaths, AnswerForm answerForm, ReferenceAnswer referenceAnswer,
                ScenarioSpec scenario, PathRecommendation recommendation, List<String> competencies, Set<ConsoleTool> tools,
                Map<String, String> params, Instant generatedAt) {
        this(id, companyId, templateId, type, level, seed, narrative, checkpoints, solutionPaths, answerForm, referenceAnswer,
                scenario, recommendation, competencies, tools, params, generatedAt, List.of(), List.of(), List.of());
    }

    public Case {
        if (solutionPaths.isEmpty()) throw new IllegalArgumentException("A case needs at least one solution path");
        if (solutionPaths.stream().noneMatch(p -> p.id().equals(recommendation.pathId())))
            throw new IllegalArgumentException("Recommended path not found: " + recommendation.pathId());
    }

    public Checkpoint checkpoint(String id) {
        return checkpoints.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown checkpoint: " + id));
    }

    public Optional<SolutionPath> solutionPath(String id) {
        return id == null ? Optional.empty() : solutionPaths.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public SolutionPath recommendedPath() { return solutionPath(recommendation.pathId()).orElseThrow(); }

    /** O caso de acompanhamento do caminho seguido, quando o template declara um. */
    public Optional<FollowUp> followUpFor(String pathId) {
        return followUps.stream().filter(f -> f.pathId().equals(pathId)).findFirst();
    }

    public Optional<CaseArtifact> artifact(String artifactId) {
        return artifacts.stream().filter(a -> a.id().equals(artifactId)).findFirst();
    }

    public Optional<Mitigation> mitigation(String id) {
        return mitigations.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** Stakeholder messages that have already arrived after this many seconds of attempt. */
    public List<StakeholderMessage> stakeholderMessagesUntil(long elapsedSeconds) {
        return stakeholderMessages.stream().filter(m -> m.atElapsedSeconds() <= elapsedSeconds).toList();
    }
}
