package dev.archsim.domain.flashcard;

import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository {
    void save(ReviewSchedule schedule);
    void saveAll(List<ReviewSchedule> schedules);
    Optional<ReviewSchedule> find(UserId userId, FlashcardId cardId);
    List<ReviewSchedule> findByUser(UserId userId);
}
