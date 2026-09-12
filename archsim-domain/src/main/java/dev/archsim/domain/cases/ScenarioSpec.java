package dev.archsim.domain.cases;

import java.util.Map;

/** Compact description from which the observability dataset is deterministically regenerated (ADR-0002). */
public record ScenarioSpec(String kind, Map<String, String> params) {
    public String param(String key) {
        String v = params.get(key);
        if (v == null) throw new IllegalArgumentException("Scenario parameter missing: " + key);
        return v;
    }
    public String paramOr(String key, String fallback) { return params.getOrDefault(key, fallback); }
}
