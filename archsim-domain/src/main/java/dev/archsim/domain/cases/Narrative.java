package dev.archsim.domain.cases;

import java.util.List;

public record Narrative(String title, String symptom, String businessContext, List<String> hints) {}
