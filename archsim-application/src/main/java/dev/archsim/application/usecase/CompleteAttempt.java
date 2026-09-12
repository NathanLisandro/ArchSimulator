package dev.archsim.application.usecase;

import dev.archsim.application.port.DomainEventPublisher;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptEvaluated;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.flashcard.Flashcard;
import dev.archsim.domain.flashcard.FlashcardFactory;
import dev.archsim.domain.flashcard.GeneratedCardRepository;
import dev.archsim.domain.progress.UserProgress;
import dev.archsim.domain.progress.UserProgressRepository;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.sprint.Sprint;
import dev.archsim.domain.sprint.SprintRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Closes the loop after an attempt is evaluated: updates the user's progress, records the result in the running
 * sprint (if any) and turns the case into flashcards. Kept apart from SubmitAnswer so the submission use case does
 * not grow a dependency on every aggregate in the system.
 */
public class CompleteAttempt {
    private final UserProgressRepository progresses;
    private final SprintRepository sprints;
    private final GeneratedCardRepository generatedCards;
    private final FlashcardFactory flashcardFactory;
    private final DomainEventPublisher events;

    public CompleteAttempt(UserProgressRepository progresses, SprintRepository sprints, GeneratedCardRepository generatedCards,
                           FlashcardFactory flashcardFactory, DomainEventPublisher events) {
        this.progresses = progresses; this.sprints = sprints; this.generatedCards = generatedCards;
        this.flashcardFactory = flashcardFactory; this.events = events;
    }

    public void execute(Case theCase, Attempt attempt, AttemptEvaluated evaluated, Instant now) {
        List<DomainEvent> produced = new ArrayList<>();

        UserProgress progress = progresses.findByUser(attempt.userId()).orElseGet(() -> UserProgress.start(attempt.userId(), now));
        progress.record(evaluated, now);
        progresses.save(progress);
        produced.addAll(progress.pullEvents());

        sprints.findRunning(attempt.userId()).ifPresent(sprint -> {
            sprint.expireIfDue(now);
            if (sprint.status() == dev.archsim.domain.sprint.SprintStatus.RUNNING) {
                sprint.recordResult(attempt.id(), theCase.type(), evaluated.score(), now);
                attempt.assignToSprint(sprint.id().value());
            }
            sprints.save(sprint);
            produced.addAll(sprint.pullEvents());
        });

        List<Flashcard> cards = flashcardFactory.fromSolvedCase(theCase, attempt);
        if (!cards.isEmpty()) generatedCards.saveAll(attempt.userId(), cards);

        if (!produced.isEmpty()) events.publish(produced);
    }

    /** Sprints that ran out of time are closed even when the user never comes back to them. */
    public void expireStaleSprints(Sprint sprint, Instant now) {
        sprint.expireIfDue(now);
        sprints.save(sprint);
        events.publish(sprint.pullEvents());
    }
}
