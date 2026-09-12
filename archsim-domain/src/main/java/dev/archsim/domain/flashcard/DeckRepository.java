package dev.archsim.domain.flashcard;

import java.util.List;
import java.util.Optional;

public interface DeckRepository {
    List<Deck> findAll();
    Optional<Deck> findById(String deckId);
    Optional<Flashcard> findCard(FlashcardId id);
}
