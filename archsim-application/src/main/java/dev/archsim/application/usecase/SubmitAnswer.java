package dev.archsim.application.usecase;

import dev.archsim.application.dto.AnswerInput;
import dev.archsim.application.dto.AttemptView;
import dev.archsim.application.port.DomainEventPublisher;
import dev.archsim.domain.attempt.AttemptEvaluated;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.FinalAnswer;
import dev.archsim.domain.attempt.StructuredFields;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.evaluation.CaseEvaluator;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

import java.util.List;

public class SubmitAnswer {
    public record Command(UserId userId, String attemptId, AnswerInput answer) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final CaseEvaluator evaluator;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final CompleteAttempt completeAttempt;

    public SubmitAnswer(AttemptRepository attempts, CaseRepository cases, CaseEvaluator evaluator, DomainEventPublisher events,
                        Clock clock, CompleteAttempt completeAttempt) {
        this.attempts = attempts; this.cases = cases; this.evaluator = evaluator; this.events = events;
        this.clock = clock; this.completeAttempt = completeAttempt;
    }

    public AttemptView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        AnswerInput in = cmd.answer();
        FinalAnswer answer = new FinalAnswer(new StructuredFields(in.fields()), in.markdown(), in.mermaid(), in.graph());
        loaded.attempt().submit(loaded.theCase(), answer, evaluator, clock.now());
        List<DomainEvent> produced = loaded.attempt().pullEvents();
        AttemptEvaluated evaluated = produced.stream().filter(AttemptEvaluated.class::isInstance)
                .map(AttemptEvaluated.class::cast).findFirst().orElseThrow();
        completeAttempt.execute(loaded.theCase(), loaded.attempt(), evaluated, clock.now());
        attempts.save(loaded.attempt());
        events.publish(produced);
        return AttemptView.from(loaded.attempt(), loaded.theCase(), clock.now());
    }
}
