package dev.archsim.domain.flashcard;

import java.util.List;
import java.util.Optional;

/** Aggregate root: a themed set of cards (consistência, escalabilidade, mensageria, ...). */
public record Deck(String id, String name, String description, List<Flashcard> cards) {
    public Optional<Flashcard> card(String cardId) {
        return cards.stream().filter(c -> c.id().value().equals(cardId)).findFirst();
    }
    public int size() { return cards.size(); }
}
