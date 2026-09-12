package dev.archsim.infrastructure.content;

import com.fasterxml.jackson.databind.JsonNode;
import dev.archsim.domain.flashcard.Deck;
import dev.archsim.domain.flashcard.DeckRepository;
import dev.archsim.domain.flashcard.Flashcard;
import dev.archsim.domain.flashcard.FlashcardId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.archsim.infrastructure.content.Yaml.*;

/** Seed decks from content/flashcards/*.yaml. One file per theme. */
public class YamlDeckRepository implements DeckRepository {
    private final Map<String, Deck> decks = new LinkedHashMap<>();
    private final Map<String, Flashcard> cardsById = new LinkedHashMap<>();

    public YamlDeckRepository(Path contentRoot) {
        for (YamlContentLoader.Loaded loaded : new YamlContentLoader().loadAll(contentRoot.resolve("flashcards"))) {
            JsonNode node = loaded.node();
            String where = loaded.path().getFileName().toString();
            String deckId = req(node, "id", where);
            List<Flashcard> cards = new ArrayList<>();
            for (JsonNode card : list(node, "cards")) {
                String cardId = deckId + ":" + req(card, "id", where);
                Flashcard flashcard = new Flashcard(new FlashcardId(cardId), deckId, req(card, "front", where),
                        req(card, "back", where), strings(card, "competencies"), null);
                cards.add(flashcard);
                cardsById.put(cardId, flashcard);
            }
            decks.put(deckId, new Deck(deckId, req(node, "name", where), opt(node, "description", ""), List.copyOf(cards)));
        }
    }

    @Override public List<Deck> findAll() { return List.copyOf(decks.values()); }
    @Override public Optional<Deck> findById(String deckId) { return Optional.ofNullable(decks.get(deckId)); }
    @Override public Optional<Flashcard> findCard(FlashcardId id) { return Optional.ofNullable(cardsById.get(id.value())); }
}
