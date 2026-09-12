package dev.archsim.application.usecase;

import dev.archsim.application.dto.AttemptView;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

/** The responder answering stakeholder pressure during the incident. */
public class PostStatusUpdate {
    public record Command(UserId userId, String attemptId, String text) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final Clock clock;

    public PostStatusUpdate(AttemptRepository attempts, CaseRepository cases, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.clock = clock;
    }

    public AttemptView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        loaded.attempt().postStatusUpdate(cmd.text(), clock.now());
        attempts.save(loaded.attempt());
        return AttemptView.from(loaded.attempt(), loaded.theCase(), clock.now());
    }
}
