package dev.archsim.domain.attempt;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.Checkpoint;
import dev.archsim.domain.cases.CheckpointOption;
import dev.archsim.domain.cases.Mitigation;
import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.cases.StakeholderMessage;
import dev.archsim.domain.evaluation.CaseEvaluator;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.user.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Aggregate root: one user's run through one case — checkpoint choices, investigation trail, final answer and evaluation. */
public class Attempt {
    private final AttemptId id;
    private final UserId userId;
    private final CaseId caseId;
    private final Instant startedAt;
    private AttemptStatus status;
    private final Map<String, CheckpointAnswer> checkpointAnswers = new LinkedHashMap<>();
    private InvestigationTrail trail;
    private FinalAnswer finalAnswer;
    private Evaluation evaluation;
    private Instant submittedAt;
    private String sprintId;
    private final List<MitigationEffectRecord> mitigations = new ArrayList<>();
    private final List<StatusUpdate> statusUpdates = new ArrayList<>();
    private Instant resolvedAt;
    private final List<DomainEvent> events = new ArrayList<>();

    private Attempt(AttemptId id, UserId userId, CaseId caseId, Instant startedAt, AttemptStatus status, InvestigationTrail trail) {
        this.id = id; this.userId = userId; this.caseId = caseId; this.startedAt = startedAt; this.status = status; this.trail = trail;
    }

    public static Attempt start(AttemptId id, UserId userId, Case theCase, Instant now) {
        return new Attempt(id, userId, theCase.id(), now, AttemptStatus.IN_PROGRESS, InvestigationTrail.empty());
    }

    /** Reconstitution from persistence; no invariants re-checked beyond nullability. */
    public static Attempt reconstitute(AttemptId id, UserId userId, CaseId caseId, Instant startedAt, AttemptStatus status,
                                       List<CheckpointAnswer> answers, InvestigationTrail trail, FinalAnswer finalAnswer,
                                       Evaluation evaluation, Instant submittedAt, String sprintId) {
        Attempt a = new Attempt(id, userId, caseId, startedAt, status, trail == null ? InvestigationTrail.empty() : trail);
        if (answers != null) answers.forEach(ans -> a.checkpointAnswers.put(ans.checkpointId(), ans));
        a.finalAnswer = finalAnswer; a.evaluation = evaluation; a.submittedAt = submittedAt; a.sprintId = sprintId;
        return a;
    }

    /** Reconstitution including incident-response state. */
    public static Attempt reconstitute(AttemptId id, UserId userId, CaseId caseId, Instant startedAt, AttemptStatus status,
                                       List<CheckpointAnswer> answers, InvestigationTrail trail, FinalAnswer finalAnswer,
                                       Evaluation evaluation, Instant submittedAt, String sprintId,
                                       List<MitigationEffectRecord> mitigations, List<StatusUpdate> statusUpdates, Instant resolvedAt) {
        Attempt a = reconstitute(id, userId, caseId, startedAt, status, answers, trail, finalAnswer, evaluation, submittedAt, sprintId);
        if (mitigations != null) a.mitigations.addAll(mitigations);
        if (statusUpdates != null) a.statusUpdates.addAll(statusUpdates);
        a.resolvedAt = resolvedAt;
        return a;
    }

    /**
     * Applies one mitigation. It counts as "informed" when the responder had already looked at something in the console
     * or answered a checkpoint; a RESOLVES mitigation ends the incident and freezes the time-to-mitigate.
     */
    public MitigationOutcome applyMitigation(Case theCase, String mitigationId, Instant now) {
        requireInProgress();
        Mitigation m = theCase.mitigation(mitigationId)
                .orElseThrow(() -> new IllegalArgumentException("Mitigação desconhecida: " + mitigationId));
        boolean informed = trail.size() > 0 || !checkpointAnswers.isEmpty();
        mitigations.add(new MitigationEffectRecord(m.id(), m.verdict(), m.effect(), informed, now));
        trail = trail.with(new ConsoleAction(dev.archsim.domain.console.ConsoleActionKind.MITIGATION, m.id(), m.label(), now));
        if (m.effect() == MitigationEffect.RESOLVES && resolvedAt == null) resolvedAt = now;
        return new MitigationOutcome(m.id(), m.verdict(), m.effect(), m.feedback(informed), resolvedAt != null);
    }

    public void postStatusUpdate(String text, Instant now) {
        requireInProgress();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("O status update não pode ser vazio");
        statusUpdates.add(new StatusUpdate(text.trim(), now, Duration.between(startedAt, now).getSeconds()));
    }

    /** Ids of stakeholder messages that already had a status update posted after they arrived. */
    public List<String> answeredStakeholders(Case theCase) {
        return theCase.stakeholderMessages().stream()
                .filter(StakeholderMessage::expectsUpdate)
                .filter(m -> statusUpdates.stream().anyMatch(u -> u.elapsedSeconds() >= m.atElapsedSeconds()))
                .map(StakeholderMessage::id).toList();
    }

    public Optional<Long> timeToMitigateSeconds() {
        return Optional.ofNullable(resolvedAt).map(r -> Duration.between(startedAt, r).getSeconds());
    }

    public DirectionalFeedback answerCheckpoint(Case theCase, String checkpointId, String optionId, boolean proceedAnyway, Instant now) {
        requireInProgress();
        if (!theCase.id().equals(caseId)) throw new IllegalArgumentException("Case does not belong to this attempt");
        Checkpoint checkpoint = theCase.checkpoint(checkpointId);
        CheckpointOption option = checkpoint.option(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown option " + optionId + " for checkpoint " + checkpointId));
        CheckpointAnswer previous = checkpointAnswers.get(checkpointId);
        int revisions = previous == null ? 0 : previous.revisions() + 1;
        boolean warns = option.verdict().warns();
        boolean confirmed = !warns || proceedAnyway;
        checkpointAnswers.put(checkpointId, new CheckpointAnswer(checkpointId, optionId, option.verdict(), confirmed, warns && proceedAnyway, revisions, now));
        return new DirectionalFeedback(checkpointId, optionId, option.verdict(), option.feedback(), warns && !proceedAnyway);
    }

    public void recordConsoleAction(ConsoleAction action) {
        requireInProgress();
        trail = trail.with(action);
    }

    public Evaluation submit(Case theCase, FinalAnswer answer, CaseEvaluator evaluator, Instant now) {
        requireInProgress();
        this.finalAnswer = answer;
        this.evaluation = evaluator.evaluate(theCase, this, now);
        this.status = AttemptStatus.SUBMITTED;
        this.submittedAt = now;
        events.add(new AttemptEvaluated(id, userId, caseId, theCase.type(), theCase.competencies(), evaluation.totalScore(), durationSeconds(), now));
        return evaluation;
    }

    /** Non-mutating view with a candidate answer attached — used by evaluators and tests. */
    public Attempt withFinalAnswer(FinalAnswer answer) {
        return reconstitute(id, userId, caseId, startedAt, status, List.copyOf(checkpointAnswers.values()), trail, answer, evaluation,
                submittedAt, sprintId, List.copyOf(mitigations), List.copyOf(statusUpdates), resolvedAt);
    }

    public void assignToSprint(String sprintId) { this.sprintId = sprintId; }

    private void requireInProgress() {
        if (status != AttemptStatus.IN_PROGRESS) throw new IllegalStateException("Attempt already submitted");
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    public long durationSeconds() {
        Instant end = submittedAt != null ? submittedAt : startedAt;
        return Duration.between(startedAt, end).getSeconds();
    }

    public AttemptId id() { return id; }
    public UserId userId() { return userId; }
    public CaseId caseId() { return caseId; }
    public Instant startedAt() { return startedAt; }
    public Instant submittedAt() { return submittedAt; }
    public AttemptStatus status() { return status; }
    public List<CheckpointAnswer> checkpointAnswers() { return List.copyOf(checkpointAnswers.values()); }
    public InvestigationTrail trail() { return trail; }
    public Optional<FinalAnswer> finalAnswer() { return Optional.ofNullable(finalAnswer); }
    public Optional<Evaluation> evaluation() { return Optional.ofNullable(evaluation); }
    public Optional<String> sprintId() { return Optional.ofNullable(sprintId); }
    public List<MitigationEffectRecord> appliedMitigations() { return List.copyOf(mitigations); }
    public List<StatusUpdate> statusUpdates() { return List.copyOf(statusUpdates); }
    public Optional<Instant> resolvedAt() { return Optional.ofNullable(resolvedAt); }
}
