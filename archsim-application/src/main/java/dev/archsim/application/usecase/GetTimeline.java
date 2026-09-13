package dev.archsim.application.usecase;

import dev.archsim.application.dto.TimelineView;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.user.UserId;

/** Infra/deploy console: what changed, what is firing, and how the infrastructure is wired. */
public class GetTimeline {
    public record Command(UserId userId, String attemptId) {}

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final CompanyRepository companies;
    private final ObservabilityDatasetProvider datasets;
    private final Clock clock;

    public GetTimeline(AttemptRepository attempts, CaseRepository cases, CompanyRepository companies,
                       ObservabilityDatasetProvider datasets, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.companies = companies; this.datasets = datasets; this.clock = clock;
    }

    public TimelineView execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        ObservabilityDataset ds = datasets.datasetFor(loaded.theCase(), loaded.attempt().resolvedAt().orElse(null));
        Company company = companies.findById(loaded.theCase().companyId()).orElseThrow();
        loaded.attempt().recordConsoleAction(new ConsoleAction(ConsoleActionKind.DEPLOYS_VIEW, "timeline", "deploys/flags/alerts/infra", clock.now()));
        attempts.save(loaded.attempt());
        return new TimelineView(ds.window().from(), ds.window().to(), ds.deploys(), ds.flagChanges(), ds.alerts(), ds.instances(),
                company.infra().resources(), company.infra().edges());
    }
}
