package dev.archsim.domain.sql;

public record JoinCondition(ColumnRef leftColumn, ColumnRef rightColumn) {}
