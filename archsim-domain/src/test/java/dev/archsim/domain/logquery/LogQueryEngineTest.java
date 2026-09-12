package dev.archsim.domain.logquery;

import dev.archsim.domain.observability.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogQueryEngineTest {
    private static final Instant T0 = Instant.parse("2026-09-12T14:00:00Z");
    private final LogQueryEngine engine = new LogQueryEngine();

    private static List<LogEntry> sample() {
        List<LogEntry> out = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Instant t = T0.plusSeconds(i * 60L);
            boolean incident = i >= 30;
            out.add(new LogEntry(t, "checkout-api", incident && i % 2 == 0 ? "ERROR" : "INFO", "tr-" + i, incident ? 3000L + i : 120L,
                    incident && i % 2 == 0 ? "Connection timeout acquiring connection from pool (waited 30000ms)" : "POST /checkout 200",
                    Map.of("pod", "checkout-api-" + (i % 3), "endpoint", "/checkout", "status", incident && i % 2 == 0 ? "503" : "200")));
            out.add(new LogEntry(t, "pix-api", "INFO", "px-" + i, 80L, "POST /pix 200", Map.of("pod", "pix-api-0", "endpoint", "/pix", "status", "200")));
            if (i % 10 == 0) out.add(new LogEntry(t, "order-service", "WARN", "or-" + i, null, "Retrying call to inventory (attempt 2)", Map.of("pod", "order-0")));
        }
        return out;
    }

    @Test
    void filtersByFieldTextAndComparisons() {
        LogQueryResult r = engine.run("service=checkout-api level=ERROR", sample());
        assertThat(r.kind()).isEqualTo(LogQueryResult.Kind.EVENTS);
        assertThat(r.events()).hasSize(15).allSatisfy(e -> assertThat(e.level()).isEqualTo("ERROR"));
        assertThat(r.events().getFirst().timestamp()).isAfter(r.events().getLast().timestamp()); // newest first

        assertThat(engine.run("\"Connection timeout\"", sample()).events()).hasSize(15);
        assertThat(engine.run("timeout pool", sample()).events()).hasSize(15);
        assertThat(engine.run("latency>1000", sample()).events()).hasSize(30);
        assertThat(engine.run("service=check* status!=200", sample()).events()).hasSize(15);
        assertThat(engine.run("pod=checkout-api-1 NOT level=ERROR", sample()).events()).allSatisfy(e -> assertThat(e.level()).isNotEqualTo("ERROR"));
        assertThat(engine.run("level=ERROR OR level=WARN", sample()).events()).hasSize(21);
    }

    @Test
    void statsCountByField() {
        LogQueryResult r = engine.run("level=ERROR | stats count by service", sample());
        assertThat(r.kind()).isEqualTo(LogQueryResult.Kind.TABLE);
        assertThat(r.columns()).containsExactly("service", "count");
        assertThat(r.rows()).containsExactly(List.of("checkout-api", 15L));

        LogQueryResult multi = engine.run("| stats count, avg(latency) as avg_latency, p95(latency), max(latency), dc(pod) by service | sort -count", sample());
        assertThat(multi.columns()).containsExactly("service", "count", "avg_latency", "p95(latency)", "max(latency)", "dc(pod)");
        assertThat(multi.rows().getFirst().getFirst()).isIn("checkout-api", "pix-api");
        assertThat(multi.rows()).hasSize(3);
        List<Object> checkout = multi.rows().stream().filter(row -> row.getFirst().equals("checkout-api")).findFirst().orElseThrow();
        assertThat((Double) checkout.get(2)).isBetween(1500.0, 1700.0);
        assertThat(checkout.get(5)).isEqualTo(3L);
    }

    @Test
    void timechartBucketsBySpan() {
        LogQueryResult r = engine.run("service=checkout-api | timechart span=10m count by level", sample());
        assertThat(r.kind()).isEqualTo(LogQueryResult.Kind.TIMECHART);
        assertThat(r.columns()).containsExactly("_time", "ERROR", "INFO");
        assertThat(r.rows()).hasSize(6);
        assertThat(r.rows().get(0).get(1)).isEqualTo(0L);
        assertThat(r.rows().get(4).get(1)).isEqualTo(5L);

        LogQueryResult avg = engine.run("service=checkout-api | timechart span=30m avg(latency)", sample());
        assertThat(avg.columns()).containsExactly("_time", "avg(latency)");
        assertThat((Double) avg.rows().get(1).get(1)).isGreaterThan(2000);
    }

    @Test
    void sortHeadFieldsAndWhere() {
        LogQueryResult r = engine.run("service=checkout-api | sort -latency | head 3 | fields timestamp, latency, message", sample());
        assertThat(r.kind()).isEqualTo(LogQueryResult.Kind.TABLE);
        assertThat(r.columns()).containsExactly("timestamp", "latency", "message");
        assertThat(r.rows()).hasSize(3);
        assertThat((Long) r.rows().get(0).get(1)).isGreaterThanOrEqualTo((Long) r.rows().get(1).get(1));

        LogQueryResult where = engine.run("| stats count by service | where count > 50", sample());
        assertThat(where.rows()).hasSize(2);
    }

    @Test
    void reportsSyntaxErrors() {
        assertThatThrownBy(() -> engine.run("| stats bogus(latency)", sample())).isInstanceOf(LogQueryException.class).hasMessageContaining("bogus");
        assertThatThrownBy(() -> engine.run("| frobnicate", sample())).isInstanceOf(LogQueryException.class).hasMessageContaining("frobnicate");
    }

    @Test
    void limitsEventsAndReportsTotals() {
        LogQueryResult r = engine.run("| head 5", sample());
        assertThat(r.events()).hasSize(5);
        assertThat(r.totalMatched()).isEqualTo(126);
        assertThat(r.truncated()).isTrue();
    }
}
