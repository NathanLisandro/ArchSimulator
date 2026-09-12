package dev.archsim.domain.flashcard;

import dev.archsim.domain.user.UserId;

import java.time.Duration;
import java.time.Instant;

/**
 * SM-2 state for one card and one user. Immutable: every review returns the next schedule.
 * Intervals: 1 day, 6 days, then previous × ease factor, with the ease factor floored at 1.3.
 */
public record ReviewSchedule(UserId userId, FlashcardId cardId, int repetitions, int intervalDays, double easeFactor,
                             int lapses, Instant dueAt, Instant lastReviewedAt) {
    public static final double MIN_EASE = 1.3;
    public static final double INITIAL_EASE = 2.5;

    public static ReviewSchedule newCard(UserId userId, FlashcardId cardId, Instant now) {
        return new ReviewSchedule(userId, cardId, 0, 0, INITIAL_EASE, 0, now, null);
    }

    public boolean isDue(Instant now) { return !now.isBefore(dueAt); }

    public ReviewSchedule review(ReviewGrade grade, Instant now) {
        double ease = Math.max(MIN_EASE, easeFactor + (0.1 - (5 - grade.quality()) * (0.08 + (5 - grade.quality()) * 0.02)));
        if (grade.forgotten()) {
            return new ReviewSchedule(userId, cardId, 0, 1, ease, lapses + 1, now.plus(Duration.ofDays(1)), now);
        }
        int nextRepetitions = repetitions + 1;
        int nextInterval = switch (nextRepetitions) {
            case 1 -> 1;
            case 2 -> 6;
            default -> (int) Math.max(1, Math.round(intervalDays * ease));
        };
        if (grade == ReviewGrade.HARD && nextRepetitions > 2) nextInterval = (int) Math.max(1, Math.round(nextInterval * 0.7));
        return new ReviewSchedule(userId, cardId, nextRepetitions, nextInterval, ease, lapses,
                now.plus(Duration.ofDays(nextInterval)), now);
    }
}
