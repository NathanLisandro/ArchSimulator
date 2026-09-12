package dev.archsim.domain.company;

public record Column(String name, String type, long distinctValues, boolean nullable) {
    public boolean isTextual() {
        String t = type.toLowerCase();
        return t.startsWith("varchar") || t.startsWith("char") || t.equals("text") || t.startsWith("varchar2");
    }
}
