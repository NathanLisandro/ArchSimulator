package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.*;
import dev.archsim.application.usecase.*;
import dev.archsim.domain.cases.Level;
import dev.archsim.infrastructure.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/attempts")
public class AttemptController {
    public record StartRequest(String companyId, String caseType, String level) {}
    public record CheckpointRequest(String optionId, Boolean proceedAnyway) {}
    public record SqlRequest(String sql) {}
    public record LogSearchRequest(String query, Instant from, Instant to) {}
    public record MitigationRequest(String mitigationId) {}
    public record StatusUpdateRequest(String text) {}

    private final StartCase startCase;
    private final GetAttempt getAttempt;
    private final AnswerCheckpoint answerCheckpoint;
    private final RunSql runSql;
    private final QueryMetrics queryMetrics;
    private final SubmitAnswer submitAnswer;
    private final SearchLogs searchLogs;
    private final GetTraces getTraces;
    private final GetTimeline getTimeline;
    private final ApplyMitigation applyMitigation;
    private final PostStatusUpdate postStatusUpdate;

    public AttemptController(StartCase startCase, GetAttempt getAttempt, AnswerCheckpoint answerCheckpoint, RunSql runSql,
                             QueryMetrics queryMetrics, SubmitAnswer submitAnswer, SearchLogs searchLogs, GetTraces getTraces,
                             GetTimeline getTimeline, ApplyMitigation applyMitigation, PostStatusUpdate postStatusUpdate) {
        this.startCase = startCase; this.getAttempt = getAttempt; this.answerCheckpoint = answerCheckpoint; this.runSql = runSql;
        this.queryMetrics = queryMetrics; this.submitAnswer = submitAnswer; this.searchLogs = searchLogs; this.getTraces = getTraces;
        this.getTimeline = getTimeline; this.applyMitigation = applyMitigation; this.postStatusUpdate = postStatusUpdate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptView start(@RequestBody(required = false) StartRequest req) {
        StartRequest r = req == null ? new StartRequest(null, null, null) : req;
        Level level = r.level() == null ? null : Level.valueOf(r.level().toUpperCase());
        return startCase.execute(new StartCase.Command(CurrentUser.id(), blankToNull(r.companyId()), blankToNull(r.caseType()), level));
    }

    @GetMapping("/{id}")
    public AttemptView get(@PathVariable String id) { return getAttempt.execute(CurrentUser.id(), id); }

    @PostMapping("/{id}/checkpoints/{checkpointId}")
    public AnswerCheckpoint.Feedback checkpoint(@PathVariable String id, @PathVariable String checkpointId, @RequestBody CheckpointRequest req) {
        return answerCheckpoint.execute(new AnswerCheckpoint.Command(CurrentUser.id(), id, checkpointId, req.optionId(), Boolean.TRUE.equals(req.proceedAnyway())));
    }

    @PostMapping("/{id}/console/sql")
    public SqlResult sql(@PathVariable String id, @RequestBody SqlRequest req) {
        return runSql.execute(new RunSql.Command(CurrentUser.id(), id, req.sql()));
    }

    @GetMapping("/{id}/console/metrics")
    public MetricsView metrics(@PathVariable String id, @RequestParam(required = false) String service,
                               @RequestParam(required = false) List<String> metric,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return queryMetrics.execute(new QueryMetrics.Command(CurrentUser.id(), id, blankToNull(service), metric, from, to));
    }

    @PostMapping("/{id}/console/logs")
    public LogSearchView logs(@PathVariable String id, @RequestBody LogSearchRequest req) {
        return searchLogs.execute(new SearchLogs.Command(CurrentUser.id(), id, req.query(), req.from(), req.to()));
    }

    @GetMapping("/{id}/console/traces")
    public TracesView traces(@PathVariable String id, @RequestParam(required = false) String traceId, @RequestParam(required = false) String service) {
        return getTraces.execute(new GetTraces.Command(CurrentUser.id(), id, blankToNull(traceId), blankToNull(service)));
    }

    @GetMapping("/{id}/console/timeline")
    public TimelineView timeline(@PathVariable String id) {
        return getTimeline.execute(new GetTimeline.Command(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/mitigations")
    public MitigationView mitigate(@PathVariable String id, @RequestBody MitigationRequest req) {
        return applyMitigation.execute(new ApplyMitigation.Command(CurrentUser.id(), id, req.mitigationId()));
    }

    @PostMapping("/{id}/status-updates")
    public AttemptView statusUpdate(@PathVariable String id, @RequestBody StatusUpdateRequest req) {
        return postStatusUpdate.execute(new PostStatusUpdate.Command(CurrentUser.id(), id, req.text()));
    }

    @PostMapping("/{id}/submit")
    public AttemptView submit(@PathVariable String id, @RequestBody AnswerInput answer) {
        return submitAnswer.execute(new SubmitAnswer.Command(CurrentUser.id(), id, answer));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
