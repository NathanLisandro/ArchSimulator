package dev.archsim.application.usecase;

import dev.archsim.application.dto.AttemptView;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

public class GetAttempt {
    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final Clock clock;

    public GetAttempt(AttemptRepository attempts, CaseRepository cases, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.clock = clock;
    }

    public AttemptView execute(UserId user, String attemptId) {
        var loaded = AttemptAccess.load(attempts, cases, user, attemptId);
        return AttemptView.from(loaded.attempt(), loaded.theCase(), clock.now());
    }
}
