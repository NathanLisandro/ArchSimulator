package dev.archsim.domain.sql;

public record ColumnRef(String alias, String column) {
    public String qualified() { return alias == null ? column : alias + "." + column; }
}
