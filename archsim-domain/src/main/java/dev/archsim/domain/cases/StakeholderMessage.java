package dev.archsim.domain.cases;

/** Pressure from the business during an incident. atElapsedSeconds is measured from the start of the attempt. */
public record StakeholderMessage(String id, String from, long atElapsedSeconds, String text, boolean expectsUpdate) {}
