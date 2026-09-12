package dev.archsim.domain.cases;

import java.util.List;

public record FieldSpec(String id, String label, FieldType type, List<FieldOption> options, boolean required, String help) {}
