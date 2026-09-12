package dev.archsim.domain.company;

import dev.archsim.domain.sql.SqlDialect;

import java.util.List;
import java.util.Optional;

public record DatabaseSchema(SqlDialect dialect, List<Table> tables) {
    public Optional<Table> table(String name) {
        return tables.stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst();
    }

    public DatabaseSchema withTable(Table replacement) {
        List<Table> updated = tables.stream().map(t -> t.name().equalsIgnoreCase(replacement.name()) ? replacement : t).toList();
        return new DatabaseSchema(dialect, updated);
    }
}
