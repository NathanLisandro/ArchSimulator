package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.flashcard.Flashcard;
import dev.archsim.domain.flashcard.FlashcardId;
import dev.archsim.domain.flashcard.GeneratedCardRepository;
import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public class JpaGeneratedCardRepository implements GeneratedCardRepository {
    private final GeneratedCardJpaRepository repository;
    private final DomainJson json;

    public JpaGeneratedCardRepository(GeneratedCardJpaRepository repository, DomainJson json) {
        this.repository = repository; this.json = json;
    }

    /** Re-solving the same case regenerates the same ids, so saving is idempotent. */
    @Override public void saveAll(UserId userId, List<Flashcard> cards) {
        repository.saveAll(cards.stream().map(c -> new GeneratedCardEntity(userId.value(), c.id().value(), c.deckId(),
                c.origin(), json.write(c))).toList());
    }

    @Override public List<Flashcard> findByUser(UserId userId) {
        return repository.findByUserId(userId.value()).stream().map(e -> json.read(e.getDocument(), Flashcard.class)).toList();
    }

    @Override public Optional<Flashcard> find(UserId userId, FlashcardId id) {
        return repository.findById(new GeneratedCardEntity.Key(userId.value(), id.value()))
                .map(e -> json.read(e.getDocument(), Flashcard.class));
    }
}
