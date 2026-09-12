package dev.archsim.domain.flashcard;

/** SM-2 quality, reduced to the four buttons the user actually sees. */
public enum ReviewGrade {
    AGAIN(0), HARD(3), GOOD(4), EASY(5);

    private final int quality;

    ReviewGrade(int quality) { this.quality = quality; }

    public int quality() { return quality; }
    public boolean forgotten() { return this == AGAIN; }
}
