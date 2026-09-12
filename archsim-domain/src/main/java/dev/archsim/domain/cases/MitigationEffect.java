package dev.archsim.domain.cases;

/** What applying a mitigation does to the simulated incident. */
public enum MitigationEffect {
    /** Stops the bleeding: the incident is considered mitigated from this instant. */
    RESOLVES,
    /** Reduces the impact without ending it. */
    PARTIAL,
    /** Changes nothing. */
    NONE,
    /** Makes things worse or destroys evidence. */
    HARMFUL
}
