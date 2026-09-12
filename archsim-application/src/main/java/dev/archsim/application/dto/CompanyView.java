package dev.archsim.application.dto;

import dev.archsim.domain.company.*;

import java.util.List;

public record CompanyView(String id, String name, String domain, String description, Scale scale, TechStack stack, Team team,
                          List<RegulatoryConstraint> regulations, Architecture architecture, InfraTopology infra,
                          List<TableSummary> tables, List<TechDebt> techDebts) {
    public record TableSummary(String name, long rowCount, long sizeMb, List<String> columns, List<String> indexes) {}

    public static CompanyView from(Company c) {
        return new CompanyView(c.id().value(), c.name(), c.domain(), c.description(), c.scale(), c.stack(), c.team(), c.regulations(),
                c.architecture(), c.infra(),
                c.schema().tables().stream().map(t -> new TableSummary(t.name(), t.rowCount(), t.sizeMb(),
                        t.columns().stream().map(col -> col.name() + " " + col.type()).toList(),
                        t.indexes().stream().map(i -> i.name() + " (" + String.join(", ", i.columns()) + ")").toList())).toList(),
                c.techDebts());
    }
}
