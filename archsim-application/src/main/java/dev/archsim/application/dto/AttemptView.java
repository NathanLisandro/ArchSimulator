package dev.archsim.application.dto;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.attempt.Evaluation;
import dev.archsim.domain.attempt.FinalAnswer;
import dev.archsim.domain.attempt.MitigationEffectRecord;
import dev.archsim.domain.attempt.StatusUpdate;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.StakeholderMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record AttemptView(String id, String userId, String status, Instant startedAt, Instant submittedAt, long durationSeconds,
                          CaseView theCase, List<ConsoleAction> trail, FinalAnswer finalAnswer, Evaluation evaluation,
                          List<MitigationEffectRecord> appliedMitigations, List<StatusUpdate> statusUpdates,
                          List<CaseView.StakeholderView> stakeholderMessages, List<CaseView.StakeholderView> pendingStakeholderMessages,
                          Instant resolvedAt, long elapsedSeconds, FollowUpView followUp) {

    /** Aparece só depois da submissão, quando o caminho seguido tem consequência de segunda ordem. */
    public record FollowUpView(String templateId, String teaser) {}

    public static AttemptView from(Attempt a, Case c) { return from(a, c, a.submittedAt() == null ? a.startedAt() : a.submittedAt()); }

    /** now drives which stakeholder messages have already arrived. */
    public static AttemptView from(Attempt a, Case c, Instant now) {
        long elapsed = Duration.between(a.startedAt(), now == null ? a.startedAt() : now).getSeconds();
        List<String> answered = a.answeredStakeholders(c);
        List<CaseView.StakeholderView> arrived = c.stakeholderMessagesUntil(elapsed).stream()
                .map(AttemptView::toView).toList();
        List<CaseView.StakeholderView> pending = c.stakeholderMessagesUntil(elapsed).stream()
                .filter(StakeholderMessage::expectsUpdate)
                .filter(m -> !answered.contains(m.id()))
                .map(AttemptView::toView).toList();
        FollowUpView followUp = a.evaluation()
                .flatMap(evaluation -> c.followUpFor(evaluation.followedPathId()))
                .map(f -> new FollowUpView(f.templateId(), f.teaser()))
                .orElse(null);
        return new AttemptView(a.id().value(), a.userId().value(), a.status().name(), a.startedAt(), a.submittedAt(), a.durationSeconds(),
                CaseView.from(c, a.checkpointAnswers()), a.trail().actions(), a.finalAnswer().orElse(null), a.evaluation().orElse(null),
                a.appliedMitigations(), a.statusUpdates(), arrived, pending, a.resolvedAt().orElse(null), Math.max(0, elapsed), followUp);
    }

    private static CaseView.StakeholderView toView(StakeholderMessage m) {
        return new CaseView.StakeholderView(m.id(), m.from(), m.text(), m.expectsUpdate(), m.atElapsedSeconds());
    }
}
