package dev.archsim.application.dto;

public record DeckView(String id, String name, String description, int totalCards, int generatedCards, long dueCount, long newCount) {}
