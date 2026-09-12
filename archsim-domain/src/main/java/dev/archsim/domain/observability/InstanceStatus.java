package dev.archsim.domain.observability;

import java.time.Instant;

public record InstanceStatus(String service, String instanceId, String status, double cpuPercent, double memoryPercent, int restarts, Instant startedAt) {}
