package dev.archsim.application.usecase;

import dev.archsim.application.NotFoundException;
import dev.archsim.application.dto.DeckView;
import dev.archsim.application.dto.ReviewCardView;
import dev.archsim.domain.flashcard.*;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.time.Instant;
import java.util.*;

/**
 * Study mode: seed decks from content plus the cards generated from solved cases, scheduled per user with SM-2.
 * A card the user has never seen is due immediately.
 */
public class StudyDecks {
    public static final int DAILY_LIMIT = 30;
    private static final String GENERATED_DECK_SUFFIX = "";

    private final DeckRepository decks;
    private final GeneratedCardRepository generatedCards;
    private final ReviewScheduleRepository schedules;
    private final Clock clock;

    public StudyDecks(DeckRepository decks, GeneratedCardRepository generatedCards, ReviewScheduleRepository schedules, Clock clock) {
        this.decks = decks; this.generatedCards = generatedCards; this.schedules = schedules; this.clock = clock;
    }

    public List<DeckView> decks(UserId user) {
        Instant now = clock.now();
        Map<String, List<Flashcard>> byDeck = cardsByDeck(user);
        Map<FlashcardId, ReviewSchedule> byCard = schedulesByCard(user);

        List<DeckView> out = new ArrayList<>();
        byDeck.forEach((deckId, cards) -> {
            long due = cards.stream().filter(c -> isDue(byCard.get(c.id()), now)).count();
            long neverSeen = cards.stream().filter(c -> !byCard.containsKey(c.id())).count();
            long generated = cards.stream().filter(Flashcard::generated).count();
            Deck seed = decks.findById(deckId).orElse(null);
            out.add(new DeckView(deckId, seed != null ? seed.name() : humanize(deckId),
                    seed != null ? seed.description() : "Cards gerados a partir dos casos resolvidos",
                    cards.size(), (int) generated, due, neverSeen));
        });
        out.sort(Comparator.comparing(DeckView::id));
        return out;
    }

    /** The next card to review in a deck, or empty when nothing is due. */
    public Optional<ReviewCardView> next(UserId user, String deckId) {
        Instant now = clock.now();
        List<Flashcard> cards = cardsByDeck(user).getOrDefault(deckId, List.of());
        if (cards.isEmpty()) throw new NotFoundException("Deck não encontrado: " + deckId);
        Map<FlashcardId, ReviewSchedule> byCard = schedulesByCard(user);

        List<Flashcard> due = cards.stream()
                .filter(c -> isDue(byCard.get(c.id()), now))
                .sorted(Comparator.comparing((Flashcard c) -> dueAt(byCard.get(c.id()), now)).thenComparing(c -> c.id().value()))
                .limit(DAILY_LIMIT)
                .toList();
        if (due.isEmpty()) return Optional.empty();

        Flashcard card = due.getFirst();
        ReviewSchedule schedule = byCard.get(card.id());
        return Optional.of(new ReviewCardView(card.id().value(), card.deckId(), card.front(), card.back(), due.size(),
                schedule == null ? 0 : schedule.repetitions(), schedule == null ? 0 : schedule.lapses()));
    }

    public ReviewCardView.Result review(UserId user, String cardId, ReviewGrade grade) {
        Instant now = clock.now();
        FlashcardId id = new FlashcardId(cardId);
        Flashcard card = findCard(user, id).orElseThrow(() -> new NotFoundException("Card não encontrado: " + cardId));
        ReviewSchedule current = schedules.find(user, id).orElseGet(() -> ReviewSchedule.newCard(user, id, now));
        ReviewSchedule next = current.review(grade, now);
        schedules.save(next);

        long remaining = next(user, card.deckId()).map(ReviewCardView::remainingToday).orElse(0L);
        return new ReviewCardView.Result(cardId, next.intervalDays(), next.dueAt(), next.easeFactor(), remaining);
    }

    private Optional<Flashcard> findCard(UserId user, FlashcardId id) {
        return decks.findCard(id).or(() -> generatedCards.find(user, id));
    }

    /** Seed cards plus this user's generated ones, keyed by deck. */
    private Map<String, List<Flashcard>> cardsByDeck(UserId user) {
        Map<String, List<Flashcard>> byDeck = new LinkedHashMap<>();
        for (Deck deck : decks.findAll()) byDeck.computeIfAbsent(deck.id(), k -> new ArrayList<>()).addAll(deck.cards());
        for (Flashcard card : generatedCards.findByUser(user)) {
            byDeck.computeIfAbsent(card.deckId() + GENERATED_DECK_SUFFIX, k -> new ArrayList<>()).add(card);
        }
        return byDeck;
    }

    private Map<FlashcardId, ReviewSchedule> schedulesByCard(UserId user) {
        Map<FlashcardId, ReviewSchedule> byCard = new HashMap<>();
        schedules.findByUser(user).forEach(s -> byCard.put(s.cardId(), s));
        return byCard;
    }

    private static boolean isDue(ReviewSchedule schedule, Instant now) { return schedule == null || schedule.isDue(now); }

    private static Instant dueAt(ReviewSchedule schedule, Instant now) { return schedule == null ? Instant.EPOCH : schedule.dueAt(); }

    private static String humanize(String deckId) {
        String spaced = deckId.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
