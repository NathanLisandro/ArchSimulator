package dev.archsim.domain.observability;

import java.time.Instant;

public record ActiveSession(int pid, String user, String application, String clientAddr, String state, String waitEventType, String waitEvent,
                            String query, Instant queryStart, long durationMs) {}
