package dev.archsim.domain.flashcard;

import java.util.List;

/** Front/back in markdown. origin is null for seed cards and carries the case id for generated ones. */
public record Flashcard(FlashcardId id, String deckId, String front, String back, List<String> competencies, String origin) {
    public Flashcard {
        if (front == null || front.isBlank()) throw new IllegalArgumentException("A frente do card não pode ser vazia");
        if (back == null || back.isBlank()) throw new IllegalArgumentException("O verso do card não pode ser vazio");
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
    }

    public boolean generated() { return origin != null; }
}
