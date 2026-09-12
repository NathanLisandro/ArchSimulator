package dev.archsim.domain.cases;

/**
 * An action the responder can take during an incident (rollback, scale out, kill a query, turn a flag off).
 * requiresDiagnosis marks actions that are only defensible after some investigation; the attempt records whether
 * the responder had looked at anything before firing it, and the feedback text differs.
 */
public record Mitigation(String id, String label, String description, Verdict verdict, MitigationEffect effect,
                         boolean requiresDiagnosis, String feedbackWhenInformed, String feedbackWhenBlind) {
    public String feedback(boolean informed) { return informed ? feedbackWhenInformed : feedbackWhenBlind; }
}
