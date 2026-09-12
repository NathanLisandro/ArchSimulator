package dev.archsim.domain.attempt;

import java.util.List;

/** Output of the drag-and-drop architecture designer. Evaluated structurally by GraphEvidence (F2b). */
public record SolutionGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
    public static SolutionGraph empty() { return new SolutionGraph(List.of(), List.of()); }
}
