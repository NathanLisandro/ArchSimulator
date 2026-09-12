package dev.archsim.application.dto;

import dev.archsim.domain.attempt.MitigationOutcome;

public record MitigationView(String mitigationId, String verdict, String effect, String message, boolean incidentResolved) {
    public static MitigationView from(MitigationOutcome o) {
        return new MitigationView(o.mitigationId(), o.verdict().name(), o.effect().name(), o.message(), o.incidentResolved());
    }
}
