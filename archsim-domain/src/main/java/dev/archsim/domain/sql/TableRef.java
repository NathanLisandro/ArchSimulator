package dev.archsim.domain.sql;

public record TableRef(String name, String alias) {
    public String displayAlias() { return alias == null ? name : alias; }
    public boolean matches(String aliasOrName) {
        return aliasOrName == null || aliasOrName.equalsIgnoreCase(alias) || aliasOrName.equalsIgnoreCase(name);
    }
}
