package dev.archsim.application.usecase;

import dev.archsim.application.dto.MitigationView;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

public class ApplyMitigation {
    public record Command(UserId userId, String attemptId, String mitigationId) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final Clock clock;

    public ApplyMitigation(AttemptRepository attempts, CaseRepository cases, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.clock = clock;
    }

    public MitigationView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        MitigationView view = MitigationView.from(loaded.attempt().applyMitigation(loaded.theCase(), cmd.mitigationId(), clock.now()));
        attempts.save(loaded.attempt());
        return view;
    }
}
