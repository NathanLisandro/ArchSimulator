package dev.archsim.domain.shared;

import java.time.Instant;

public interface Clock {
    Instant now();
}
