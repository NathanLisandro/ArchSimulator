package dev.archsim.domain.flashcard;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Picks what to review now: everything due, most overdue first, capped by the daily limit. */
public record ReviewSession(List<ReviewSchedule> schedules) {
    public List<FlashcardId> dueCards(Instant now, int limit) {
        return schedules.stream()
                .filter(s -> s.isDue(now))
                .sorted(Comparator.comparing(ReviewSchedule::dueAt).thenComparing(s -> s.cardId().value()))
                .limit(Math.max(1, limit))
                .map(ReviewSchedule::cardId)
                .toList();
    }

    public long dueCount(Instant now) { return schedules.stream().filter(s -> s.isDue(now)).count(); }
}
