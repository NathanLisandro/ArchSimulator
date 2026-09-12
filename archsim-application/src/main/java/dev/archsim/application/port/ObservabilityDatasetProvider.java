package dev.archsim.application.port;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.observability.ObservabilityDataset;

/** Returns the (possibly cached) dataset regenerated from the case seed. */
public interface ObservabilityDatasetProvider {
    ObservabilityDataset datasetFor(Case theCase);
}
