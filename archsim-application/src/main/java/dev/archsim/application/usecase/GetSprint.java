package dev.archsim.application.usecase;

import dev.archsim.application.ForbiddenException;
import dev.archsim.application.NotFoundException;
import dev.archsim.application.dto.SprintView;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.sprint.Sprint;
import dev.archsim.domain.sprint.SprintId;
import dev.archsim.domain.sprint.SprintRepository;
import dev.archsim.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public class GetSprint {
    private final SprintRepository sprints;
    private final Clock clock;

    public GetSprint(SprintRepository sprints, Clock clock) { this.sprints = sprints; this.clock = clock; }

    public SprintView execute(UserId user, String sprintId) {
        Sprint sprint = sprints.findById(SprintId.of(sprintId)).orElseThrow(() -> new NotFoundException("Sprint não encontrado: " + sprintId));
        if (!sprint.userId().equals(user)) throw new ForbiddenException("Este sprint pertence a outro usuário");
        sprint.expireIfDue(clock.now());
        sprints.save(sprint);
        return SprintView.from(sprint, clock.now());
    }

    public Optional<SprintView> running(UserId user) {
        return sprints.findRunning(user).map(s -> {
            s.expireIfDue(clock.now());
            sprints.save(s);
            return SprintView.from(s, clock.now());
        });
    }

    public List<SprintView> history(UserId user) {
        return sprints.findByUser(user).stream().map(s -> SprintView.from(s, clock.now())).toList();
    }
}
