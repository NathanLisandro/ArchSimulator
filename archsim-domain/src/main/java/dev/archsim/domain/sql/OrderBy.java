package dev.archsim.domain.sql;

public record OrderBy(ColumnRef column, boolean descending) {}
