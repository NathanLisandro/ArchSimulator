package dev.archsim.domain.company;

import java.util.List;

public record TechStack(List<String> languages, List<String> frameworks, List<String> datastores, List<String> messaging, String cloud, String notes) {}
