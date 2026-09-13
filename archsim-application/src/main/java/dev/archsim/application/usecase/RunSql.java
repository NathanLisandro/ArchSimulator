package dev.archsim.application.usecase;

import dev.archsim.application.dto.SqlResult;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.application.port.SqlSandbox;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.attempt.ConsoleAction;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.sql.ExplainPlan;
import dev.archsim.domain.sql.ExplainSimulator;
import dev.archsim.domain.user.UserId;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SQL console: EXPLAIN is simulated by the domain planner; SELECTs run in the read-only sandbox. Every call is recorded in the trail. */
public class RunSql {
    public record Command(UserId userId, String attemptId, String sql) {}

    private static final Pattern TABLE_NAMES = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-z_][a-z0-9_$.]*)");
    private static final Pattern READ_ONLY = Pattern.compile("(?is)^\\s*(select|with|explain|show)\\b");

    private final AttemptRepository attempts;
    private final CaseRepository cases;
    private final CompanyRepository companies;
    private final ObservabilityDatasetProvider datasets;
    private final SqlSandbox sandbox;
    private final ExplainSimulator explain;
    private final Clock clock;

    public RunSql(AttemptRepository attempts, CaseRepository cases, CompanyRepository companies, ObservabilityDatasetProvider datasets,
                  SqlSandbox sandbox, ExplainSimulator explain, Clock clock) {
        this.attempts = attempts; this.cases = cases; this.companies = companies; this.datasets = datasets; this.sandbox = sandbox; this.explain = explain; this.clock = clock;
    }

    public SqlResult execute(Command cmd) {
        var loaded = AttemptAccess.load(attempts, cases, cmd.userId(), cmd.attemptId());
        String sql = cmd.sql() == null ? "" : cmd.sql().trim();
        if (!READ_ONLY.matcher(sql).find()) return SqlResult.error("Console somente leitura: use SELECT, WITH ou EXPLAIN.");

        Company company = companies.findById(loaded.theCase().companyId()).orElseThrow();
        ObservabilityDataset dataset = datasets.datasetFor(loaded.theCase(), loaded.attempt().resolvedAt().orElse(null));
        String target = String.join(",", tableNames(sql));
        boolean isExplain = sql.toLowerCase(Locale.ROOT).startsWith("explain");

        SqlResult result;
        if (isExplain) {
            ExplainPlan plan = explain.explain(sql, dataset.dbState().effectiveSchema(), company.schema().dialect());
            result = plan.error() ? SqlResult.error(plan.text()) : SqlResult.plan(plan.text());
        } else {
            result = sandbox.execute(loaded.theCase().id(), dataset.dbState(), company.schema().dialect(), sql);
        }
        loaded.attempt().recordConsoleAction(new ConsoleAction(isExplain ? ConsoleActionKind.EXPLAIN : ConsoleActionKind.SQL_QUERY, target, sql, clock.now()));
        attempts.save(loaded.attempt());
        return result;
    }

    static Set<String> tableNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = TABLE_NAMES.matcher(sql);
        while (m.find()) names.add(m.group(1).toLowerCase(Locale.ROOT));
        return names;
    }
}
