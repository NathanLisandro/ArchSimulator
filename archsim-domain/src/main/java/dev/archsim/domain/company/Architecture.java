package dev.archsim.domain.company;

import java.util.List;

public record Architecture(List<ServiceComponent> services, List<Datastore> datastores, List<MessageBroker> brokers, List<CacheComponent> caches) {}
