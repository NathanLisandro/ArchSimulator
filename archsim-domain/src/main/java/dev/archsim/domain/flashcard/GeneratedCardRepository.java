package dev.archsim.domain.flashcard;

import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

/** Cards produced from solved cases, which live per user rather than in the seed decks. */
public interface GeneratedCardRepository {
    void saveAll(UserId userId, List<Flashcard> cards);
    List<Flashcard> findByUser(UserId userId);
    Optional<Flashcard> find(UserId userId, FlashcardId id);
}
