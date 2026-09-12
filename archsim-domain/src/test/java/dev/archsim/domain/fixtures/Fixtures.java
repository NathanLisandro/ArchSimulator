package dev.archsim.domain.fixtures;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.template.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Fixtures {
    private Fixtures() {}

    public static CaseTemplate slowQueryTemplate() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("p99", new ParameterSpec.IntRange(3000, 6000, 100));
        params.put("rows", new ParameterSpec.IntRange(40_000_000, 120_000_000, 1_000_000));
        params.put("sinceHoursAgo", new ParameterSpec.IntRange(6, 30, 1));
        params.put("target", new ParameterSpec.Group(List.of(
                Map.of("endpoint", "GET /accounts/{id}/statement", "service", "account-api"),
                Map.of("endpoint", "GET /receivables/schedule", "service", "receivables-api"))));
        Map<String, String> derived = new LinkedHashMap<>();
        derived.put("p99Before", "round(p99 / 20)");
        Checkpoint cp = new Checkpoint("first-tool", "Por onde começa?", List.of(
                new CheckpointOption("metrics", "Métricas por serviço", Verdict.GOOD, "Boa: o p99 de {{p99}}ms está só em {{service}}."),
                new CheckpointOption("logs-db", "Logs do banco", Verdict.BAD, "Você foi direto ao banco, mas com {{rows|compact}} linhas o sinal está no p99 de {{service}}.")));
        Rubric rubric = new Rubric(List.of(new Criterion("root-cause", "Causa raiz", 40,
                List.of(new ChoiceEvidence("rootCause", List.of("MISSING_INDEX"))), "Não identificou a causa", "Causa raiz correta")));
        SolutionPath idx = new SolutionPath("composite-index", "Índice composto", "resumo", List.of("rápido"), List.of("escrita {{rows|compact}}"), "Quando a query filtra por igualdade + range", rubric);
        SolutionPath cache = new SolutionPath("cache", "Cache", "resumo", List.of(), List.of("stale"), "Quando leitura repetida", rubric);
        AnswerForm form = new AnswerForm(List.of(
                new FieldSpec("rootCause", "Causa raiz", FieldType.SELECT, List.of(new FieldOption("MISSING_INDEX", "Índice ausente")), true, null),
                new FieldSpec("solutionPath", "Caminho", FieldType.SELECT, List.of(new FieldOption("composite-index", "Índice"), new FieldOption("cache", "Cache")), true, null)), "solutionPath");
        return new CaseTemplate("t1", CaseType.SLOW_QUERY, new CompanyId("banco-digital"), List.of(Level.JUNIOR, Level.SENIOR), List.of("databases"),
                params, derived,
                new Narrative("{{endpoint}} em {{p99}}ms", "p99 de {{p99}}ms desde {{sinceLabel}} em {{service}}; tabela com {{rows|number}} linhas (antes: {{p99Before}}ms)", "contexto", List.of("Olhe o EXPLAIN")),
                List.of(cp), List.of(idx, cache), form, "Referência: {{rows|compact}} linhas",
                new ScenarioSpec("slow-query", Map.of("service", "{{service}}", "sinceAt", "{{sinceHoursAgoAt}}")),
                "composite-index", List.of(new RecommendationRule("rows", ">", 100_000_000, "cache", "com {{rows|compact}} linhas o índice sozinho não segura")),
                Set.of(ConsoleTool.SQL, ConsoleTool.METRICS));
    }


    public static dev.archsim.domain.company.Company bank() {
        var ledger = new dev.archsim.domain.company.Table("ledger_entries", 80_000_000L, 42_000L,
                List.of(new dev.archsim.domain.company.Column("id", "bigint", 80_000_000L, false),
                        new dev.archsim.domain.company.Column("account_id", "bigint", 2_000_000L, false),
                        new dev.archsim.domain.company.Column("created_at", "timestamp", 60_000_000L, false),
                        new dev.archsim.domain.company.Column("amount_cents", "bigint", 500_000L, false),
                        new dev.archsim.domain.company.Column("status", "varchar(16)", 4L, false)),
                List.of(new dev.archsim.domain.company.Index("ledger_entries_pkey", List.of("id"), true, "btree", 1_700L),
                        new dev.archsim.domain.company.Index("idx_ledger_account_created", List.of("account_id", "created_at"), false, "btree", 3_000L)));
        var accounts = new dev.archsim.domain.company.Table("accounts", 2_000_000L, 800L,
                List.of(new dev.archsim.domain.company.Column("id", "bigint", 2_000_000L, false),
                        new dev.archsim.domain.company.Column("document", "varchar(14)", 2_000_000L, false)),
                List.of(new dev.archsim.domain.company.Index("accounts_pkey", List.of("id"), true, "btree", 45L)));
        return new dev.archsim.domain.company.Company(new CompanyId("banco-digital"), "Banco Digital", "banking", "desc",
                new dev.archsim.domain.company.Scale(3_000_000, 1200, "40 TB", "crescendo 8% ao mês"),
                new dev.archsim.domain.company.TechStack(List.of("Java"), List.of("Spring"), List.of("PostgreSQL"), List.of("Kafka"), "AWS", null),
                new dev.archsim.domain.company.Team(80, 10, null), List.of(),
                new dev.archsim.domain.company.Architecture(
                        List.of(new dev.archsim.domain.company.ServiceComponent("account-api", "contas", "Java", List.of(), "core-db", 6),
                                new dev.archsim.domain.company.ServiceComponent("pix-api", "pix", "Java", List.of(), "core-db", 8),
                                new dev.archsim.domain.company.ServiceComponent("receivables-api", "agenda", "Java", List.of(), "core-db", 4)),
                        List.of(new dev.archsim.domain.company.Datastore("core-db", "postgres", "RDS", "db.r6g.4xlarge")),
                        List.of(), List.of()),
                new dev.archsim.domain.company.InfraTopology(List.of(), List.of()),
                new dev.archsim.domain.company.DatabaseSchema(dev.archsim.domain.sql.SqlDialect.POSTGRES, List.of(ledger, accounts)),
                List.of());
    }
}
