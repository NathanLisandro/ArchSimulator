package dev.archsim.domain.rubric;

/**
 * Structural check over the architecture the user drew (drag-and-drop graph, or Mermaid as fallback).
 *
 * <ul>
 *   <li>NODE_OF_TYPE — at least minCount nodes of type {@code value} (queue, cache, database, service...)</li>
 *   <li>NODE_LABELED — at least minCount nodes whose label contains {@code value}</li>
 *   <li>EDGE — an edge from {@code from} to {@code to}, in any style</li>
 *   <li>ASYNC_EDGE — that edge exists and is asynchronous</li>
 *   <li>NO_SYNC_EDGE — there is no synchronous edge between the pair (a forbidden coupling)</li>
 *   <li>EDGE_PROPERTY — some edge carries {@code key=value} (outbox, idempotent, retry...)</li>
 * </ul>
 */
public record GraphEvidence(Kind kind, String value, String from, String to, int minCount) implements Evidence {
    public enum Kind { NODE_OF_TYPE, NODE_LABELED, EDGE, ASYNC_EDGE, NO_SYNC_EDGE, EDGE_PROPERTY }

    @Override public boolean matches(AnswerContext ctx) { return ctx.graphMatches(this); }

    @Override public String describe() {
        return switch (kind) {
            case NODE_OF_TYPE -> "componente do tipo " + value;
            case NODE_LABELED -> "componente chamado " + value;
            case EDGE -> "ligação " + from + " → " + to;
            case ASYNC_EDGE -> "ligação assíncrona " + from + " → " + to;
            case NO_SYNC_EDGE -> "sem chamada síncrona " + from + " → " + to;
            case EDGE_PROPERTY -> "ligação com " + value;
        };
    }
}
