package dev.archsim.domain.attempt;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Values of the structured answer fields keyed by field id; multi-selects are comma-separated. */
public record StructuredFields(Map<String, String> values) {
    public StructuredFields { values = values == null ? Map.of() : Map.copyOf(values); }
    public String value(String id) { return values.getOrDefault(id, ""); }
    public List<String> valuesOf(String id) {
        String v = value(id);
        return v.isBlank() ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
