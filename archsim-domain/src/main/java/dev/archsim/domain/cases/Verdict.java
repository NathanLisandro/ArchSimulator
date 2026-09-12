package dev.archsim.domain.cases;

public enum Verdict {
    GOOD, RISKY, BAD;
    public boolean warns() { return this != GOOD; }
    public double judgmentScore() { return switch (this) { case GOOD -> 1.0; case RISKY -> 0.5; case BAD -> 0.0; }; }
}
