package dev.archsim.application.dto;

import dev.archsim.domain.company.InfraEdge;
import dev.archsim.domain.company.InfraResource;
import dev.archsim.domain.observability.Alert;
import dev.archsim.domain.observability.DeployEvent;
import dev.archsim.domain.observability.FeatureFlagChange;
import dev.archsim.domain.observability.InstanceStatus;

import java.time.Instant;
import java.util.List;

/** "What changed": deploys, flags, alerts, instance health and the infra map, on one time axis. */
public record TimelineView(Instant from, Instant to, List<DeployEvent> deploys, List<FeatureFlagChange> flagChanges,
                           List<Alert> alerts, List<InstanceStatus> instances, List<InfraResource> infraResources, List<InfraEdge> infraEdges) {}
