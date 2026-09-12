package dev.archsim.domain.flashcard;

import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.CheckpointAnswer;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.Checkpoint;
import dev.archsim.domain.cases.CheckpointOption;
import dev.archsim.domain.cases.SolutionPath;
import dev.archsim.domain.cases.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a solved case into study material. Two sources, both already written by the template author:
 * the directional feedback of the checkpoints (especially the ones the user got wrong) and the
 * "when is this path the right one" of each solution path.
 *
 * <p>Card ids are derived from the case, so solving the same case twice does not duplicate cards.
 */
public final class FlashcardFactory {
    private static final int MAX_CARDS_PER_CASE = 8;

    public List<Flashcard> fromSolvedCase(Case theCase, Attempt attempt) {
        List<Flashcard> cards = new ArrayList<>();
        String origin = theCase.id().value();
        String deck = deckFor(theCase);

        for (Checkpoint checkpoint : theCase.checkpoints()) {
            CheckpointOption best = checkpoint.options().stream().filter(o -> o.verdict() == Verdict.GOOD).findFirst().orElse(null);
            if (best == null) continue;
            boolean gotItWrong = attempt.checkpointAnswers().stream()
                    .anyMatch(a -> a.checkpointId().equals(checkpoint.id()) && a.verdict() != Verdict.GOOD);
            CheckpointOption mistake = gotItWrong ? mistakenOption(checkpoint, attempt) : null;

            String back = "**" + best.label() + "**\n\n" + best.feedback();
            if (mistake != null) back += "\n\n_Você escolheu:_ " + mistake.label() + " — " + mistake.feedback();
            cards.add(new Flashcard(new FlashcardId(origin + ":cp:" + checkpoint.id()), deck,
                    checkpoint.question(), back, theCase.competencies(), origin));
        }

        for (SolutionPath path : theCase.solutionPaths()) {
            if (path.bestWhen() == null || path.bestWhen().isBlank()) continue;
            String front = "Quando preferir **" + path.name() + "**?";
            StringBuilder back = new StringBuilder(capitalise(path.bestWhen()) + ".");
            if (!path.pros().isEmpty()) back.append("\n\n**A favor:** ").append(String.join("; ", path.pros()));
            if (!path.cons().isEmpty()) back.append("\n\n**Custo:** ").append(String.join("; ", path.cons()));
            cards.add(new Flashcard(new FlashcardId(origin + ":path:" + path.id()), deck, front, back.toString(),
                    theCase.competencies(), origin));
        }

        SolutionPath recommended = theCase.recommendedPath();
        cards.add(new Flashcard(new FlashcardId(origin + ":recommendation"), deck,
                "No cenário \"" + theCase.narrative().title() + "\", qual caminho é o mais indicado e por quê?",
                "**" + recommended.name() + "** — " + theCase.recommendation().reason() + ".",
                theCase.competencies(), origin));

        return cards.stream().limit(MAX_CARDS_PER_CASE).toList();
    }

    private static CheckpointOption mistakenOption(Checkpoint checkpoint, Attempt attempt) {
        return attempt.checkpointAnswers().stream()
                .filter(a -> a.checkpointId().equals(checkpoint.id()))
                .findFirst()
                .map(CheckpointAnswer::optionId)
                .flatMap(checkpoint::option)
                .orElse(null);
    }

    /** Generated cards land in the deck of the case's first competency, falling back to the case type. */
    private static String deckFor(Case theCase) {
        return theCase.competencies().isEmpty()
                ? theCase.type().name().toLowerCase(Locale.ROOT).replace('_', '-')
                : theCase.competencies().getFirst();
    }

    private static String capitalise(String text) {
        return text.isBlank() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
