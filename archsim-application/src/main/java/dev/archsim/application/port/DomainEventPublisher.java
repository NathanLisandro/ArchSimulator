package dev.archsim.application.port;

import dev.archsim.domain.shared.DomainEvent;

import java.util.List;

public interface DomainEventPublisher {
    void publish(List<DomainEvent> events);
}
