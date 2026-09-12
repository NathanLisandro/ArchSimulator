package dev.archsim.domain.cases;

import java.util.List;
import java.util.Optional;

public record Checkpoint(String id, String question, List<CheckpointOption> options) {
    public Optional<CheckpointOption> option(String optionId) {
        return options.stream().filter(o -> o.id().equals(optionId)).findFirst();
    }
}
