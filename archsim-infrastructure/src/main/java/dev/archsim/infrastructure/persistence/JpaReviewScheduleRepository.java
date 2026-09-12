package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.flashcard.FlashcardId;
import dev.archsim.domain.flashcard.ReviewSchedule;
import dev.archsim.domain.flashcard.ReviewScheduleRepository;
import dev.archsim.domain.user.UserId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public class JpaReviewScheduleRepository implements ReviewScheduleRepository {
    private final ReviewScheduleJpaRepository repository;

    public JpaReviewScheduleRepository(ReviewScheduleJpaRepository repository) { this.repository = repository; }

    @Override public void save(ReviewSchedule s) { repository.save(toEntity(s)); }

    @Override public void saveAll(List<ReviewSchedule> schedules) { repository.saveAll(schedules.stream().map(JpaReviewScheduleRepository::toEntity).toList()); }

    @Override public Optional<ReviewSchedule> find(UserId userId, FlashcardId cardId) {
        return repository.findById(new ReviewScheduleEntity.Key(userId.value(), cardId.value())).map(JpaReviewScheduleRepository::toDomain);
    }

    @Override public List<ReviewSchedule> findByUser(UserId userId) {
        return repository.findByUserId(userId.value()).stream().map(JpaReviewScheduleRepository::toDomain).toList();
    }

    private static ReviewScheduleEntity toEntity(ReviewSchedule s) {
        return new ReviewScheduleEntity(s.userId().value(), s.cardId().value(), s.repetitions(), s.intervalDays(),
                BigDecimal.valueOf(s.easeFactor()).setScale(2, RoundingMode.HALF_UP), s.lapses(), s.dueAt(), s.lastReviewedAt());
    }

    private static ReviewSchedule toDomain(ReviewScheduleEntity e) {
        return new ReviewSchedule(new UserId(e.getUserId()), new FlashcardId(e.getCardId()), e.getRepetitions(),
                e.getIntervalDays(), e.getEaseFactor().doubleValue(), e.getLapses(), e.getDueAt(), e.getLastReviewedAt());
    }
}
