package dev.archsim.domain.company;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Table(String name, long rowCount, long sizeMb, List<Column> columns, List<Index> indexes) {
    public Optional<Column> column(String name) {
        return columns.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    public Table withIndex(Index index) {
        List<Index> updated = new ArrayList<>(indexes);
        updated.add(index);
        return new Table(name, rowCount, sizeMb, columns, List.copyOf(updated));
    }

    public Table withoutIndex(String indexName) {
        return new Table(name, rowCount, sizeMb, columns, indexes.stream().filter(i -> !i.name().equalsIgnoreCase(indexName)).toList());
    }

    /** Approximate 8KB pages. */
    public long pages() { return Math.max(1, sizeMb * 1024 / 8); }
}
