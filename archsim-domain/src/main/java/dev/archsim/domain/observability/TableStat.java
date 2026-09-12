package dev.archsim.domain.observability;

import java.time.Instant;

public record TableStat(String table, long seqScan, long seqTupRead, long idxScan, long liveRows, long deadRows, long sizeMb, Instant lastAutovacuum) {}
