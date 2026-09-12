package dev.archsim.domain.observability;

/** pg_stat_statements / v$sql style row. */
public record StatementStat(String query, long calls, double totalMs, double meanMs, double maxMs, long rows, long sharedBlksRead, long sharedBlksHit) {}
