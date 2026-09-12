package dev.archsim.application.usecase;

import dev.archsim.application.dto.AttemptSummaryView;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.user.UserId;

import java.util.Comparator;
import java.util.List;

public class ListAttempts {
    private final AttemptRepository attempts;
    private final CaseRepository cases;

    public ListAttempts(AttemptRepository attempts, CaseRepository cases) { this.attempts = attempts; this.cases = cases; }

    public List<AttemptSummaryView> execute(UserId user) {
        return attempts.findByUser(user).stream()
                .sorted(Comparator.comparing(Attempt::startedAt).reversed())
                .flatMap(a -> cases.findById(a.caseId()).map(c -> AttemptSummaryView.from(a, c)).stream())
                .toList();
    }
}
