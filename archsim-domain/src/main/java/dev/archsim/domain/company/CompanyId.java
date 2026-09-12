package dev.archsim.domain.company;

public record CompanyId(String value) {
    public CompanyId { if (value == null || value.isBlank()) throw new IllegalArgumentException("CompanyId required"); }
}
