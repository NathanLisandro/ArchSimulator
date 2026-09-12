package dev.archsim.domain.company;

import java.util.List;

public record ServiceComponent(String name, String description, String language, List<String> dependsOn, String datastore, int instances) {}
