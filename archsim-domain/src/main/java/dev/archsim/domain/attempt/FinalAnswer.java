package dev.archsim.domain.attempt;

public record FinalAnswer(StructuredFields fields, String markdown, String mermaid, SolutionGraph graph) {
    public FinalAnswer {
        fields = fields == null ? new StructuredFields(java.util.Map.of()) : fields;
        markdown = markdown == null ? "" : markdown;
    }
}
