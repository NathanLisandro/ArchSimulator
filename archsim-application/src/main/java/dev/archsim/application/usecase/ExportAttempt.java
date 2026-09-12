package dev.archsim.application.usecase;

import dev.archsim.application.dto.ExportView;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.export.MarkdownExporter;
import dev.archsim.domain.user.UserId;

public class ExportAttempt {
    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final MarkdownExporter exporter;

    public ExportAttempt(AttemptRepository attempts, CaseRepository cases, MarkdownExporter exporter) {
        this.attempts = attempts; this.cases = cases; this.exporter = exporter;
    }

    public ExportView execute(UserId user, String attemptId) {
        var loaded = AttemptAccess.load(attempts, cases, user, attemptId);
        return new ExportView(exporter.fileName(loaded.theCase(), loaded.attempt()),
                exporter.export(loaded.theCase(), loaded.attempt()));
    }
}
