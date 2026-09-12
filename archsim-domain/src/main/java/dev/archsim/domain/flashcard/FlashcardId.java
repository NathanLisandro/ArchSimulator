package dev.archsim.domain.flashcard;

public record FlashcardId(String value) {
    public FlashcardId { if (value == null || value.isBlank()) throw new IllegalArgumentException("FlashcardId required"); }
}
