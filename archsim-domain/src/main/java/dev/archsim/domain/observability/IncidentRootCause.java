package dev.archsim.domain.observability;

/**
 * Catalogue of root causes an incident scenario can express. Each one has a recognisable signature across
 * metrics, logs, traces and the deploy timeline; the generator paints that signature over healthy-looking noise.
 */
public enum IncidentRootCause {
    /** A deploy turned one query into a loop of queries: latency up, DB read IOPS up, trace with dozens of identical spans. */
    N_PLUS_ONE("N+1 após deploy"),
    /** An index was dropped/invalidated: one statement starts scanning, DB CPU climbs, no code change. */
    INDEX_REMOVED("Índice removido"),
    /** Requests queue waiting for a JDBC connection: pool at max, timeouts, latency piles up on every endpoint of the service. */
    POOL_EXHAUSTION("Saturação do pool de conexões"),
    /** One Kafka partition gets most keys: consumer lag on a single partition, others healthy. */
    KAFKA_HOT_PARTITION("Hot partition no Kafka"),
    /** A failing dependency plus aggressive retries multiplies traffic and keeps the dependency down. */
    RETRY_STORM("Retry storm"),
    /** Cache entries created together expire together: hit ratio collapses, origin gets a stampede. */
    CACHE_STAMPEDE("Expiração em massa do cache"),
    /** A batch job holds a long transaction/lock: writers block, wait events on Lock, latency spikes on writes. */
    LONG_LOCK("Lock longo por batch"),
    /** Heap grows across the window, GC pauses lengthen, pods restart with OOM. */
    MEMORY_LEAK("Memory leak"),
    /** A third party (or DNS to it) slows down: errors and latency concentrated on the egress path. */
    THIRD_PARTY_TIMEOUT("Timeout de terceiro / DNS"),
    /** Clock skew between pods breaks time-based idempotency keys: duplicates appear without any error. */
    CLOCK_SKEW("Clock skew na idempotência"),
    /** Messages fail and pile up in the dead-letter queue while every latency metric stays green. */
    DLQ_GROWING("Fila DLQ crescendo em silêncio"),
    /** A feature flag turned on an expensive path for a fraction of traffic: bimodal latency, no deploy. */
    FEATURE_FLAG("Feature flag ligada");

    private final String label;

    IncidentRootCause(String label) { this.label = label; }

    public String label() { return label; }

    public static IncidentRootCause of(String name) {
        for (IncidentRootCause c : values()) if (c.name().equalsIgnoreCase(name)) return c;
        throw new IllegalArgumentException("Causa raiz desconhecida: " + name);
    }
}
