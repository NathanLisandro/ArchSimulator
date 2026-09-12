package dev.archsim.domain.evaluation;

import dev.archsim.domain.attempt.GraphEdge;
import dev.archsim.domain.attempt.GraphNode;
import dev.archsim.domain.attempt.SolutionGraph;
import dev.archsim.domain.rubric.GraphEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The architecture the user drew, normalised for structural checks. It comes from the drag-and-drop editor
 * ({@link SolutionGraph}) or, when there is none, from a Mermaid flowchart the user typed.
 */
final class DrawnArchitecture {
    /** a -->|label| b, a -.-> b, a ==> b, with optional [shape] on either side. */
    private static final Pattern MERMAID_EDGE = Pattern.compile(
            "(?m)^\\s*([A-Za-z0-9_.-]+)(?:[\\[(\\{][^\\])}]*[\\])\\}])?\\s*(-{2,3}>|-\\.-+>|={2,3}>|--[ox]|-\\.->)\\s*(?:\\|([^|]*)\\|)?\\s*([A-Za-z0-9_.-]+)");
    private static final Pattern MERMAID_NODE = Pattern.compile(
            "(?m)^\\s*([A-Za-z0-9_.-]+)\\s*[\\[(\\{]+\\s*\"?([^\\]\"})]+)\"?\\s*[\\])}]+");
    /** Words that reveal the kind of a component when the type is not declared (Mermaid case). */
    private static final Map<String, List<String>> TYPE_HINTS = Map.of(
            "queue", List.of("queue", "fila", "topic", "tópico", "kafka", "sqs", "sns", "rabbit", "stream", "kinesis", "outbox"),
            "cache", List.of("cache", "redis", "memcached", "elasticache", "cdn", "cloudfront"),
            "database", List.of("db", "database", "banco", "postgres", "mysql", "oracle", "dynamo", "mongo", "rds"),
            "service", List.of("api", "service", "serviço", "servico", "worker", "job", "lambda", "consumer", "gateway"));

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;

    private DrawnArchitecture(List<GraphNode> nodes, List<GraphEdge> edges) { this.nodes = nodes; this.edges = edges; }

    static DrawnArchitecture of(SolutionGraph graph, String mermaid) {
        if (graph != null && !graph.nodes().isEmpty()) return new DrawnArchitecture(graph.nodes(), graph.edges());
        if (mermaid != null && !mermaid.isBlank()) return fromMermaid(mermaid);
        return new DrawnArchitecture(List.of(), List.of());
    }

    boolean matches(GraphEvidence e) {
        int min = Math.max(1, e.minCount());
        return switch (e.kind()) {
            case NODE_OF_TYPE -> nodes.stream().filter(n -> typeOf(n).equals(lower(e.value()))).count() >= min;
            case NODE_LABELED -> nodes.stream().filter(n -> lower(n.label()).contains(lower(e.value()))).count() >= min;
            case EDGE -> edgesBetween(e.from(), e.to()).size() >= min;
            case ASYNC_EDGE -> edgesBetween(e.from(), e.to()).stream().filter(GraphEdge::async).count() >= min;
            case NO_SYNC_EDGE -> edgesBetween(e.from(), e.to()).stream().noneMatch(edge -> !edge.async());
            case EDGE_PROPERTY -> edges.stream().filter(edge -> hasProperty(edge, e.value())).count() >= min;
        };
    }

    private List<GraphEdge> edgesBetween(String from, String to) {
        List<GraphEdge> out = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (matchesNode(edge.from(), from) && matchesNode(edge.to(), to)) out.add(edge);
        }
        return out;
    }

    /** An endpoint matches by node id or by node label, both compared loosely. */
    private boolean matchesNode(String endpointId, String wanted) {
        if (wanted == null || wanted.isBlank()) return true;
        if (loosely(endpointId).contains(loosely(wanted))) return true;
        return node(endpointId).map(n -> loosely(n.label()).contains(loosely(wanted))).orElse(false);
    }

    private Optional<GraphNode> node(String id) { return nodes.stream().filter(n -> n.id().equals(id)).findFirst(); }

    private static boolean hasProperty(GraphEdge edge, String keyValue) {
        String[] parts = keyValue.split("=", 2);
        String key = parts[0].trim();
        String expected = parts.length > 1 ? parts[1].trim() : null;
        String actual = edge.props().get(key);
        if (actual == null) {
            // Mermaid fallback: the property may be written in the edge label ("publica (outbox)").
            return expected != null && lower(edge.label()).contains(lower(expected));
        }
        return expected == null || lower(actual).contains(lower(expected));
    }

    /** Declared type when the editor produced it; otherwise inferred from the label. */
    private static String typeOf(GraphNode n) {
        if (n.type() != null && !n.type().isBlank() && !n.type().equals("component")) return lower(n.type());
        String label = lower(n.label());
        for (Map.Entry<String, List<String>> e : TYPE_HINTS.entrySet()) {
            if (e.getValue().stream().anyMatch(label::contains)) return e.getKey();
        }
        return "component";
    }

    private static DrawnArchitecture fromMermaid(String mermaid) {
        List<GraphNode> nodes = new ArrayList<>();
        Matcher nodeMatcher = MERMAID_NODE.matcher(mermaid);
        while (nodeMatcher.find()) nodes.add(new GraphNode(nodeMatcher.group(1), "component", nodeMatcher.group(2).trim(), Map.of()));

        List<GraphEdge> edges = new ArrayList<>();
        Matcher edgeMatcher = MERMAID_EDGE.matcher(mermaid);
        while (edgeMatcher.find()) {
            String from = edgeMatcher.group(1);
            String arrow = edgeMatcher.group(2);
            String label = edgeMatcher.group(3) == null ? "" : edgeMatcher.group(3).trim();
            String to = edgeMatcher.group(4);
            boolean async = arrow.contains(".") || isAsyncLabel(label) || touchesQueue(nodes, from, to);
            edges.add(new GraphEdge(from, to, label, async, Map.of()));
            addIfMissing(nodes, from);
            addIfMissing(nodes, to);
        }
        return new DrawnArchitecture(List.copyOf(nodes), List.copyOf(edges));
    }

    private static boolean isAsyncLabel(String label) {
        String l = lower(label);
        return l.contains("publica") || l.contains("publish") || l.contains("consome") || l.contains("consume")
                || l.contains("event") || l.contains("async") || l.contains("assínc") || l.contains("assinc")
                || l.contains("mensagem") || l.contains("message");
    }

    /** An edge touching a queue/topic node is asynchronous even when the arrow is solid. */
    private static boolean touchesQueue(List<GraphNode> nodes, String from, String to) {
        return nodes.stream().filter(n -> n.id().equals(from) || n.id().equals(to)).anyMatch(n -> typeOf(n).equals("queue"))
                || isQueueName(from) || isQueueName(to);
    }

    private static boolean isQueueName(String id) {
        return TYPE_HINTS.get("queue").stream().anyMatch(hint -> lower(id).contains(hint));
    }

    private static void addIfMissing(List<GraphNode> nodes, String id) {
        if (nodes.stream().noneMatch(n -> n.id().equals(id))) nodes.add(new GraphNode(id, "component", id, Map.of()));
    }

    private static String lower(String v) { return v == null ? "" : v.toLowerCase(Locale.ROOT); }

    /** Ignores case, accents-free comparison is not needed here; hyphens and underscores are. */
    private static String loosely(String v) { return lower(v).replace("_", "-").replace(" ", "-"); }
}
