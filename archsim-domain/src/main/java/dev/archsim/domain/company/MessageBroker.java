package dev.archsim.domain.company;

import java.util.List;

public record MessageBroker(String name, String type, List<String> topics) {}
