package dev.archsim.application.usecase;

import dev.archsim.application.dto.SprintView;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.sprint.Sprint;
import dev.archsim.domain.sprint.SprintId;
import dev.archsim.domain.sprint.SprintRepository;
import dev.archsim.domain.user.UserId;

import java.time.Duration;

/** Starting a sprint closes any previous one that is still open. */
public class StartSprint {
    public record Command(UserId userId, int totalCases, Duration timeLimit, Level level, CaseType caseType) {}

    private final SprintRepository sprints;
    private final Clock clock;

    public StartSprint(SprintRepository sprints, Clock clock) { this.sprints = sprints; this.clock = clock; }

    public SprintView execute(Command cmd) {
        sprints.findRunning(cmd.userId()).ifPresent(previous -> {
            previous.expireIfDue(clock.now());
            sprints.save(previous);
        });
        Sprint sprint = Sprint.start(SprintId.random(), cmd.userId(), cmd.totalCases(), cmd.timeLimit(),
                cmd.level() == null ? Level.JUNIOR : cmd.level(), cmd.caseType(), clock.now());
        sprints.save(sprint);
        return SprintView.from(sprint, clock.now());
    }
}
