package dev.archsim.domain.cases;

public enum Level {
    JUNIOR, PLENO, SENIOR, STAFF;
    public boolean atLeast(Level other) { return ordinal() >= other.ordinal(); }
    public boolean showsHints() { return this == JUNIOR || this == PLENO; }
}
