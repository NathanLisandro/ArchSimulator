package dev.archsim.domain.company;

import java.util.List;
import java.util.Optional;

public record InfraTopology(List<InfraResource> resources, List<InfraEdge> edges) {
    public Optional<InfraResource> resource(String id) {
        return resources.stream().filter(r -> r.id().equals(id)).findFirst();
    }
}
