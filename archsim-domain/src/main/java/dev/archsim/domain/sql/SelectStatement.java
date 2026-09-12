package dev.archsim.domain.sql;

import java.util.List;
import java.util.Optional;

public record SelectStatement(List<TableRef> tables, List<JoinCondition> joins, List<Predicate> predicates,
                              List<OrderBy> orderBy, Integer limit, boolean aggregate, String selectList) {
    public Optional<TableRef> tableFor(String alias) {
        if (alias == null) return tables.size() == 1 ? Optional.of(tables.getFirst()) : Optional.empty();
        return tables.stream().filter(t -> t.matches(alias)).findFirst();
    }
}
