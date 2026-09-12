package dev.archsim.application.usecase;

import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.DirectionalFeedback;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

public class AnswerCheckpoint {
    public record Command(UserId userId, String attemptId, String checkpointId, String optionId, boolean proceedAnyway) {}
    public record Feedback(String checkpointId, String optionId, String verdict, String message, boolean requiresConfirmation) {
        static Feedback from(DirectionalFeedback f) { return new Feedback(f.checkpointId(), f.optionId(), f.verdict().name(), f.message(), f.requiresConfirmation()); }
    }

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final Clock clock;

    public AnswerCheckpoint(AttemptRepository attempts, CaseRepository cases, Clock clock) { this.attempts = attempts; this.cases = cases; this.clock = clock; }

    public Feedback execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        DirectionalFeedback fb = loaded.attempt().answerCheckpoint(loaded.theCase(), cmd.checkpointId(), cmd.optionId(), cmd.proceedAnyway(), clock.now());
        attempts.save(loaded.attempt());
        return Feedback.from(fb);
    }
}
