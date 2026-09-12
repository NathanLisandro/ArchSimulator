package dev.archsim.application.usecase;

import dev.archsim.application.dto.AttemptSummaryView;
import dev.archsim.application.dto.ProgressView;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.progress.UserProgress;
import dev.archsim.domain.progress.UserProgressRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.util.Comparator;
import java.util.List;

public class GetProgress {
    static final int RECENT_ATTEMPTS = 10;

    private final UserProgressRepository progresses;
    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final Clock clock;

    public GetProgress(UserProgressRepository progresses, AttemptRepository attempts, CaseRepository cases, Clock clock) {
        this.progresses = progresses; this.attempts = attempts; this.cases = cases; this.clock = clock;
    }

    public ProgressView execute(UserId user) {
        UserProgress progress = progresses.findByUser(user).orElseGet(() -> UserProgress.start(user, clock.now()));
        List<AttemptSummaryView> recent = attempts.findByUser(user).stream()
                .sorted(Comparator.comparing(Attempt::startedAt).reversed())
                .limit(RECENT_ATTEMPTS)
                .flatMap(a -> cases.findById(a.caseId()).map(c -> AttemptSummaryView.from(a, c)).stream())
                .toList();
        return ProgressView.from(progress, recent);
    }
}
