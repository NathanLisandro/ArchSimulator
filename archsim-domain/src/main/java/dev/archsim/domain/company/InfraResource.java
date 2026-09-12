package dev.archsim.domain.company;

import java.util.Map;

/** A cloud resource typed by the infra catalog (e.g. aws.rds.postgres, aws.elasticache.redis, aws.sqs.queue). */
public record InfraResource(String id, String type, String name, Map<String, String> properties) {}
