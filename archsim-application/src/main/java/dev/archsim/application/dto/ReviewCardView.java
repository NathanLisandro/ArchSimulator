package dev.archsim.application.dto;

import java.time.Instant;

public record ReviewCardView(String cardId, String deckId, String front, String back, long remainingToday, int repetitions, int lapses) {
    /** What the user sees after grading a card. */
    public record Result(String cardId, int intervalDays, Instant dueAt, double easeFactor, long remainingToday) {}
}
