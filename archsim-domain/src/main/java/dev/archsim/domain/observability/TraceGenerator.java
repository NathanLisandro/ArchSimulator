package dev.archsim.domain.observability;

import dev.archsim.domain.shared.RandomSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds distributed traces whose span breakdown shows where the time goes. */
public final class TraceGenerator {
    private final RandomSource random;

    public TraceGenerator(RandomSource random) { this.random = random; }

    /** A healthy request: HTTP span, a couple of DB calls, one downstream call. */
    public Trace healthy(String traceId, String service, String operation, Instant at) {
        List<Span> spans = new ArrayList<>();
        long dbMs = random.nextLong(3, 18);
        long downstreamMs = random.nextLong(8, 40);
        long total = dbMs + downstreamMs + random.nextLong(5, 25);
        spans.add(new Span("s0", null, service, operation, 0, total, Map.of("http.status_code", "200")));
        spans.add(new Span("s1", "s0", service, "SELECT FROM accounts WHERE id = ?", 2, dbMs, Map.of("db.system", "postgresql")));
        spans.add(new Span("s2", "s0", "fraud-scoring", "POST /score", 2 + dbMs, downstreamMs, Map.of("http.status_code", "200")));
        return new Trace(traceId, at, total, service, operation, List.copyOf(spans));
    }

    /** N+1: one parent span and many identical child queries, each cheap, together dominating the request. */
    public Trace nPlusOne(String traceId, String service, String operation, String table, Instant at, int queries) {
        List<Span> spans = new ArrayList<>();
        long perQuery = random.nextLong(3, 9);
        long offset = 6;
        List<Span> children = new ArrayList<>();
        for (int i = 0; i < queries; i++) {
            children.add(new Span("s" + (i + 2), "s1", service, "SELECT FROM " + table + " WHERE id = ?", offset, perQuery,
                    Map.of("db.system", "postgresql", "db.rows", "1")));
            offset += perQuery;
        }
        long total = offset + random.nextLong(8, 30);
        spans.add(new Span("s0", null, service, operation, 0, total, Map.of("http.status_code", "200")));
        spans.add(new Span("s1", "s0", service, "loadRelatedEntries", 4, offset - 4, Map.of("code.function", "loadRelatedEntries")));
        spans.addAll(children);
        return new Trace(traceId, at, total, service, operation, List.copyOf(spans));
    }

    /** One span dominates the request (a scanning query, a slow third party, a lock wait). */
    public Trace dominatedBy(String traceId, String service, String operation, String slowService, String slowOperation,
                             long slowMs, Instant at, Map<String, String> tags) {
        long overhead = random.nextLong(10, 45);
        long total = slowMs + overhead;
        List<Span> spans = List.of(
                new Span("s0", null, service, operation, 0, total, Map.of("http.status_code", tags.getOrDefault("http.status_code", "200"))),
                new Span("s1", "s0", service, "authorize", 2, random.nextLong(2, 8), Map.of()),
                new Span("s2", "s0", slowService, slowOperation, 8, slowMs, tags));
        return new Trace(traceId, at, total, service, operation, spans);
    }
}
