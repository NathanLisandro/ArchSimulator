package dev.archsim.application.fakes;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.*;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.sql.SqlDialect;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.ParameterSpec;
import dev.archsim.domain.template.RecommendationRule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestTemplates {
    public static Company bank() {
        Table ledger = new Table("ledger_entries", 80_000_000L, 42_000L,
                List.of(new Column("id", "bigint", 80_000_000L, false), new Column("account_id", "bigint", 2_000_000L, false),
                        new Column("created_at", "timestamp", 60_000_000L, false), new Column("status", "varchar(16)", 4L, false)),
                List.of(new Index("ledger_entries_pkey", List.of("id"), true, "btree", 1_700L)));
        return new Company(new CompanyId("banco-digital"), "Banco Digital", "banking", "d", new Scale(3_000_000, 1200, "40 TB", ""),
                new TechStack(List.of("Java"), List.of(), List.of(), List.of(), "AWS", null), new Team(80, 10, null), List.of(),
                new Architecture(List.of(new ServiceComponent("account-api", "", "Java", List.of(), "core-db", 4),
                        new ServiceComponent("pix-api", "", "Java", List.of(), "core-db", 4)),
                        List.of(new Datastore("core-db", "postgres", "", "")), List.of(), List.of()),
                new InfraTopology(
                        List.of(new InfraResource("rds-core", "aws.rds.postgres", "RDS core-db", Map.of("instanceClass", "db.r6g.8xlarge")),
                                new InfraResource("eks-main", "aws.eks", "EKS prod", Map.of("nodes", "24"))),
                        List.of(new InfraEdge("eks-main", "rds-core", "JDBC"))),
                new DatabaseSchema(SqlDialect.POSTGRES, List.of(ledger)), List.of());
    }

    public static CaseTemplate incident(Level... levels) {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("p99", new ParameterSpec.IntRange(3000, 6000, 100));
        params.put("onsetMinutesAgo", new ParameterSpec.IntRange(20, 40, 5));
        params.put("deployMinutesAgo", new ParameterSpec.IntRange(45, 60, 5));
        Rubric rubric = new Rubric(List.of(
                new Criterion("mitigation", "Mitigação", 50, List.of(new MitigationEvidence(null, MitigationEffect.RESOLVES, true)), "faltou mitigar", "mitigou"),
                new Criterion("postmortem", "Post-mortem", 50, List.of(new SectionEvidence(List.of("Impacto", "Causa raiz"))), "faltou post-mortem", "post-mortem ok")));
        List<Mitigation> mitigations = List.of(
                new Mitigation("rollback-deploy", "Rollback do deploy", "Reverte a release", Verdict.GOOD, MitigationEffect.RESOLVES, true,
                        "Rollback com diagnóstico: p99 volta em 4 min.", "Rollback sem evidência: deu certo por sorte."),
                new Mitigation("restart-pods", "Reiniciar pods", "Recria os pods", Verdict.BAD, MitigationEffect.HARMFUL, false,
                        "Restart destrói a evidência.", "Restart destrói a evidência."));
        List<StakeholderMessage> messages = List.of(
                new StakeholderMessage("cto", "Marina (CTO)", 300, "O app está fora para os clientes?", true),
                new StakeholderMessage("support", "Rafael (Suporte)", 1200, "Previsão de normalização?", true));
        return new CaseTemplate("bank-incident-1", CaseType.INCIDENT, new CompanyId("banco-digital"), List.of(levels), List.of("incident"),
                params, Map.of(),
                new Narrative("Incidente: p99 em {{p99}}ms", "p99 {{p99}}ms desde {{onsetLabel}}", "ctx", List.of()),
                List.of(new Checkpoint("first-tool", "Por onde?", List.of(
                        new CheckpointOption("metrics", "Métricas", Verdict.GOOD, "boa"),
                        new CheckpointOption("restart", "Reiniciar", Verdict.BAD, "ruim")))),
                List.of(new SolutionPath("rollback", "Rollback", "s", List.of("a"), List.of("b"), "quando", rubric),
                        new SolutionPath("hotfix", "Hotfix", "s", List.of("a"), List.of("b"), "quando", rubric)),
                new AnswerForm(List.of(new FieldSpec("solutionPath", "Caminho", FieldType.SELECT, List.of(), true, null),
                        new FieldSpec("postMortem", "Post-mortem", FieldType.MARKDOWN, List.of(), true, null)), "solutionPath"),
                "ref {{p99}}",
                new ScenarioSpec("incident", Map.of("rootCause", "N_PLUS_ONE", "service", "account-api", "datastore", "core-db",
                        "onsetAt", "{{onsetMinutesAgoAt}}", "deployAt", "{{deployMinutesAgoAt}}", "deployService", "account-api",
                        "deployVersion", "2026.37.4", "table", "ledger_entries", "p99Before", "180", "p99After", "{{p99}}")),
                "rollback", List.of(), Set.of(ConsoleTool.LOGS, ConsoleTool.METRICS, ConsoleTool.TRACES, ConsoleTool.DEPLOYS, ConsoleTool.INFRA),
                mitigations, messages);
    }

    public static CaseTemplate slowQuery(Level... levels) {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("p99", new ParameterSpec.IntRange(3000, 6000, 100));
        params.put("sinceHoursAgo", new ParameterSpec.IntRange(6, 20, 1));
        Rubric rubric = new Rubric(List.of(
                new Criterion("root-cause", "Causa raiz", 50, List.of(new ChoiceEvidence("rootCause", List.of("MISSING_INDEX"))), "faltou causa", "causa ok"),
                new Criterion("investigation", "Investigação", 50, List.of(new InvestigationEvidence(ConsoleActionKind.EXPLAIN, "ledger_entries", 1)), "sem explain", "explain ok")));
        return new CaseTemplate("bank-slow-1", CaseType.SLOW_QUERY, new CompanyId("banco-digital"), List.of(levels), List.of("databases"), params, Map.of(),
                new Narrative("Extrato lento: {{p99}}ms", "p99 {{p99}}ms desde {{sinceLabel}}", "ctx", List.of("dica")),
                List.of(new Checkpoint("first-tool", "Por onde?", List.of(new CheckpointOption("metrics", "Métricas", Verdict.GOOD, "boa"),
                        new CheckpointOption("logs", "Logs", Verdict.BAD, "ruim: {{p99}}ms")))),
                List.of(new SolutionPath("composite-index", "Índice composto", "s", List.of("a"), List.of("b"), "quando", rubric),
                        new SolutionPath("cache", "Cache", "s", List.of("a"), List.of("b"), "quando", rubric)),
                new AnswerForm(List.of(new FieldSpec("rootCause", "Causa", FieldType.SELECT, List.of(new FieldOption("MISSING_INDEX", "Índice ausente")), true, null),
                        new FieldSpec("solutionPath", "Caminho", FieldType.SELECT, List.of(), true, null),
                        new FieldSpec("explanation", "Explicação", FieldType.MARKDOWN, List.of(), true, null)), "solutionPath"),
                "ref {{p99}}",
                new ScenarioSpec("slow-query", Map.of("service", "account-api", "endpoint", "GET /statement", "table", "ledger_entries",
                        "slowSql", "SELECT * FROM ledger_entries WHERE account_id = $1 ORDER BY created_at DESC LIMIT 50",
                        "p99Before", "180", "p99After", "{{p99}}", "sinceAt", "{{sinceHoursAgoAt}}")),
                "composite-index", List.of(), Set.of(ConsoleTool.SQL, ConsoleTool.METRICS));
    }
}
