package dev.archsim.domain.cases;

/** Material the case hands over for the user to read: a code snippet, a stored procedure, a Mermaid diagram, a config. */
public record CaseArtifact(String id, String title, ArtifactKind kind, String language, String content) {}
