package dev.archsim.application.port;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.observability.ObservabilityDataset;

import java.time.Instant;

/**
 * Returns the (possibly cached) dataset regenerated from the case seed. When the responder already mitigated the
 * incident, recoveredAt makes the telemetry recover from that instant — a resolved incident has to look resolved.
 */
public interface ObservabilityDatasetProvider {
    ObservabilityDataset datasetFor(Case theCase, Instant recoveredAt);

    default ObservabilityDataset datasetFor(Case theCase) { return datasetFor(theCase, null); }
}
