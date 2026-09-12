package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.SprintView;
import dev.archsim.application.usecase.GetSprint;
import dev.archsim.application.usecase.StartSprint;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.infrastructure.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {
    public record StartRequest(Integer totalCases, Integer minutes, String level, String caseType) {}

    private final StartSprint startSprint;
    private final GetSprint getSprint;

    public SprintController(StartSprint startSprint, GetSprint getSprint) { this.startSprint = startSprint; this.getSprint = getSprint; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SprintView start(@RequestBody(required = false) StartRequest req) {
        StartRequest r = req == null ? new StartRequest(null, null, null, null) : req;
        return startSprint.execute(new StartSprint.Command(CurrentUser.id(),
                r.totalCases() == null ? 5 : r.totalCases(),
                Duration.ofMinutes(r.minutes() == null ? 75 : r.minutes()),
                r.level() == null ? Level.JUNIOR : Level.valueOf(r.level().toUpperCase()),
                r.caseType() == null || r.caseType().isBlank() ? null : CaseType.valueOf(r.caseType().toUpperCase())));
    }

    @GetMapping("/current")
    public ResponseEntity<SprintView> current() {
        return getSprint.running(CurrentUser.id()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public SprintView get(@PathVariable String id) { return getSprint.execute(CurrentUser.id(), id); }

    @GetMapping
    public List<SprintView> history() { return getSprint.history(CurrentUser.id()); }
}
