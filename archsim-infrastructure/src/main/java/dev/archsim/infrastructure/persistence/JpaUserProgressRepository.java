package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.progress.TypeStats;
import dev.archsim.domain.progress.UserProgress;
import dev.archsim.domain.progress.UserProgressRepository;
import dev.archsim.domain.user.UserId;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class JpaUserProgressRepository implements UserProgressRepository {
    /** JSON body: the two stats maps, which are read as a whole and never queried by field. */
    record Document(Map<String, TypeStats> byType, Map<String, TypeStats> byCompetency) {}

    private final ProgressJpaRepository repository;
    private final DomainJson json;

    public JpaUserProgressRepository(ProgressJpaRepository repository, DomainJson json) {
        this.repository = repository; this.json = json;
    }

    @Override public void save(UserProgress progress) {
        Map<String, TypeStats> byType = new LinkedHashMap<>();
        progress.statsByType().forEach((type, stats) -> byType.put(type.name(), stats));
        repository.save(new UserProgressEntity(progress.userId().value(), progress.rank().name(), progress.xp(),
                progress.solvedCount(), BigDecimal.valueOf(progress.totalScore()), progress.updatedAt(),
                json.write(new Document(byType, progress.statsByCompetency()))));
    }

    @Override public Optional<UserProgress> findByUser(UserId userId) {
        return repository.findById(userId.value()).map(e -> {
            Document document = json.read(e.getDocument(), Document.class);
            Map<CaseType, TypeStats> byType = new LinkedHashMap<>();
            document.byType().forEach((type, stats) -> byType.put(CaseType.valueOf(type), stats));
            return UserProgress.reconstitute(new UserId(e.getUserId()), Level.valueOf(e.getRank()), e.getXp(),
                    e.getSolvedCount(), e.getTotalScore().doubleValue(), byType, document.byCompetency(), e.getUpdatedAt());
        });
    }
}
