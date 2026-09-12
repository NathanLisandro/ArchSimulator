package dev.archsim.application.usecase;

import dev.archsim.application.ForbiddenException;
import dev.archsim.application.NotFoundException;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.user.UserId;

/** Loads an attempt and its case, enforcing ownership. */
final class AttemptAccess {
    record Loaded(Attempt attempt, Case theCase) {}

    static Loaded load(AttemptRepository attempts, CaseRepository cases, UserId user, String attemptId) {
        Attempt attempt = attempts.findById(AttemptId.of(attemptId)).orElseThrow(() -> new NotFoundException("Tentativa não encontrada: " + attemptId));
        if (!attempt.userId().equals(user)) throw new ForbiddenException("Esta tentativa pertence a outro usuário");
        Case c = cases.findById(attempt.caseId()).orElseThrow(() -> new NotFoundException("Caso não encontrado: " + attempt.caseId().value()));
        return new Loaded(attempt, c);
    }

    private AttemptAccess() {}
}
