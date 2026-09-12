package dev.archsim.domain.attempt;

import java.time.Instant;

public record StatusUpdate(String text, Instant postedAt, long elapsedSeconds) {}
