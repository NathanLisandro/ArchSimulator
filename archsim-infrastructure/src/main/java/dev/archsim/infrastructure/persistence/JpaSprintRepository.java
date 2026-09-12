package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.sprint.*;
import dev.archsim.domain.user.UserId;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class JpaSprintRepository implements SprintRepository {
    record Document(List<SprintResult> results) {}

    private final SprintJpaRepository repository;
    private final DomainJson json;

    public JpaSprintRepository(SprintJpaRepository repository, DomainJson json) { this.repository = repository; this.json = json; }

    @Override public void save(Sprint sprint) {
        repository.save(new SprintEntity(sprint.id().value(), sprint.userId().value(), sprint.status().name(),
                sprint.totalCases(), sprint.timeLimit().getSeconds(), sprint.level().name(),
                sprint.caseType().map(Enum::name).orElse(null), sprint.startedAt(), sprint.finishedAt().orElse(null),
                json.write(new Document(sprint.results()))));
    }

    @Override public Optional<Sprint> findById(SprintId id) { return repository.findById(id.value()).map(this::toDomain); }

    @Override public List<Sprint> findByUser(UserId userId) {
        return repository.findByUserIdOrderByStartedAtDesc(userId.value()).stream().map(this::toDomain).toList();
    }

    @Override public Optional<Sprint> findRunning(UserId userId) {
        return repository.findFirstByUserIdAndStatus(userId.value(), SprintStatus.RUNNING.name()).map(this::toDomain);
    }

    private Sprint toDomain(SprintEntity e) {
        Document document = json.read(e.getDocument(), Document.class);
        return Sprint.reconstitute(SprintId.of(e.getId()), new UserId(e.getUserId()), e.getTotalCases(),
                Duration.ofSeconds(e.getTimeLimitSeconds()), Level.valueOf(e.getLevel()),
                e.getCaseType() == null ? null : CaseType.valueOf(e.getCaseType()), e.getStartedAt(),
                SprintStatus.valueOf(e.getStatus()), e.getFinishedAt(), document.results());
    }
}
