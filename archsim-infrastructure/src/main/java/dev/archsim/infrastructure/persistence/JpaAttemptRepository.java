package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.AttemptStatus;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.user.UserId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class JpaAttemptRepository implements AttemptRepository {
    private final AttemptJpaRepository attempts;
    private final DomainJson json;
    public JpaAttemptRepository(AttemptJpaRepository attempts, DomainJson json) { this.attempts = attempts; this.json = json; }

    @Override public void save(Attempt a) {
        BigDecimal score = a.evaluation().map(e -> BigDecimal.valueOf(e.totalScore())).orElse(null);
        attempts.save(new AttemptEntity(a.id().value(), a.userId().value(), a.caseId().value(), a.status().name(), a.startedAt(), a.submittedAt(),
                score, a.sprintId().orElse(null), json.write(AttemptDocument.from(a))));
    }

    @Override public Optional<Attempt> findById(AttemptId id) { return attempts.findById(id.value()).map(this::toDomain); }
    @Override public List<Attempt> findByUser(UserId userId) { return attempts.findByUserIdOrderByStartedAtDesc(userId.value()).stream().map(this::toDomain).toList(); }

    private Attempt toDomain(AttemptEntity e) {
        AttemptDocument d = json.read(e.getDocument(), AttemptDocument.class);
        return Attempt.reconstitute(AttemptId.of(e.getId()), new UserId(e.getUserId()), CaseId.of(e.getCaseId()), e.getStartedAt(),
                AttemptStatus.valueOf(e.getStatus()), d.checkpointAnswers(), d.trail(), d.finalAnswer(), d.evaluation(), e.getSubmittedAt(),
                e.getSprintId(), d.mitigations(), d.statusUpdates(), d.resolvedAt());
    }
}
