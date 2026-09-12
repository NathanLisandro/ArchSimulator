package dev.archsim.domain.company;

import java.util.List;

public record Index(String name, List<String> columns, boolean unique, String type, long sizeMb) {
    public String leadingColumn() { return columns.getFirst(); }
}
