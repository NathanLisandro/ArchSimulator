package dev.archsim.domain.observability;

import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.ServiceComponent;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TimeWindow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces structured JSON-like log lines. Healthy traffic for every service is the noise; the incident signature is
 * painted on top by {@link #incidentLines}. Trace ids are shared with the trace generator so the console correlates.
 */
public final class LogGenerator {
    private static final List<String> ENDPOINTS = List.of("/health", "/metrics", "/v1/accounts", "/v1/pix", "/v1/receivables", "/v1/cards");

    private final TimeWindow window;
    private final RandomSource random;

    public LogGenerator(TimeWindow window, RandomSource random) { this.window = window; this.random = random; }

    /** Background traffic: INFO with occasional WARN/ERROR, so the incident does not stand out by mere existence. */
    public List<LogEntry> baseline(Company company, int linesPerService) {
        List<LogEntry> out = new ArrayList<>();
        long spanSeconds = window.duration().getSeconds();
        for (ServiceComponent service : company.architecture().services()) {
            for (int i = 0; i < linesPerService; i++) {
                Instant at = window.from().plusSeconds(random.nextLong(0, spanSeconds));
                double roll = random.nextDouble();
                String level = roll < 0.006 ? "ERROR" : roll < 0.05 ? "WARN" : "INFO";
                long latency = Math.round(20 + Math.abs(random.nextGaussian()) * 45);
                String endpoint = random.pick(ENDPOINTS);
                String message = switch (level) {
                    case "ERROR" -> random.pick(List.of(
                            "Unhandled exception mapping request: NullPointerException",
                            "Client closed request before response",
                            "Failed to publish event, will retry: TimeoutException"));
                    case "WARN" -> random.pick(List.of(
                            "Slow response detected", "Circuit breaker half-open", "Deserialization fallback applied",
                            "Retrying call to downstream (attempt 2)"));
                    default -> "GET " + endpoint + " 200";
                };
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("pod", service.name() + "-" + random.nextInt(1, Math.max(2, service.instances())));
                fields.put("endpoint", endpoint);
                fields.put("status", level.equals("ERROR") ? "500" : "200");
                fields.put("env", "prod");
                out.add(new LogEntry(at, service.name(), level, traceId(), latency, message, Map.copyOf(fields)));
            }
        }
        return out;
    }

    /** Incident lines for one service between onset and the end of the window, at the given rate per minute. */
    public List<LogEntry> incidentLines(String service, Instant onset, int linesPerMinute, String level, List<String> messages,
                                        long latencyBase, Map<String, String> extraFields, List<String> traceIds) {
        List<LogEntry> out = new ArrayList<>();
        long minutes = Math.max(1, java.time.Duration.between(onset, window.to()).toMinutes());
        for (long m = 0; m < minutes; m++) {
            for (int i = 0; i < linesPerMinute; i++) {
                Instant at = onset.plusSeconds(m * 60 + random.nextLong(0, 59));
                if (!window.contains(at)) continue;
                Map<String, String> fields = new LinkedHashMap<>(extraFields);
                fields.putIfAbsent("env", "prod");
                long latency = latencyBase <= 0 ? 0 : Math.round(latencyBase * (0.8 + random.nextDouble() * 0.5));
                String trace = traceIds.isEmpty() || random.nextDouble() < 0.6 ? traceId() : random.pick(traceIds);
                out.add(new LogEntry(at, service, level, trace, latency <= 0 ? null : latency, random.pick(messages), Map.copyOf(fields)));
            }
        }
        return out;
    }

    public String traceId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append("0123456789abcdef".charAt(random.nextInt(0, 15)));
        return sb.toString();
    }
}
