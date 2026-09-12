package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.AttemptSummaryView;
import dev.archsim.application.dto.ExportView;
import dev.archsim.application.dto.ProgressView;
import dev.archsim.application.usecase.ExportAttempt;
import dev.archsim.application.usecase.GetProgress;
import dev.archsim.application.usecase.ListAttempts;
import dev.archsim.infrastructure.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProgressController {
    private final GetProgress getProgress;
    private final ListAttempts listAttempts;
    private final ExportAttempt exportAttempt;

    public ProgressController(GetProgress getProgress, ListAttempts listAttempts, ExportAttempt exportAttempt) {
        this.getProgress = getProgress; this.listAttempts = listAttempts; this.exportAttempt = exportAttempt;
    }

    @GetMapping("/progress")
    public ProgressView progress() { return getProgress.execute(CurrentUser.id()); }

    @GetMapping("/attempts")
    public List<AttemptSummaryView> history() { return listAttempts.execute(CurrentUser.id()); }

    @GetMapping(value = "/attempts/{id}/export", produces = "text/markdown")
    public ResponseEntity<String> export(@PathVariable String id) {
        ExportView view = exportAttempt.execute(CurrentUser.id(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + view.fileName() + "\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(view.markdown());
    }
}
