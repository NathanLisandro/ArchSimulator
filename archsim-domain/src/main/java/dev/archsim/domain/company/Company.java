package dev.archsim.domain.company;

import java.util.List;
import java.util.Optional;

/** Aggregate root: the business context every case references. Loaded from content YAML, immutable. */
public record Company(CompanyId id, String name, String domain, String description, Scale scale, TechStack stack,
                      Team team, List<RegulatoryConstraint> regulations, Architecture architecture,
                      InfraTopology infra, DatabaseSchema schema, List<TechDebt> techDebts) {

    public Optional<ServiceComponent> service(String name) {
        return architecture.services().stream().filter(s -> s.name().equals(name)).findFirst();
    }

    public Optional<Table> table(String name) {
        return schema.table(name);
    }
}
