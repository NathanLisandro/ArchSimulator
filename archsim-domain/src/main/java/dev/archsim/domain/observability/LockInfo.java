package dev.archsim.domain.observability;

public record LockInfo(int pid, String relation, String lockType, String mode, boolean granted, long waitingMs) {}
