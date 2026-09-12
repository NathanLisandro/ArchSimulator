package dev.archsim.infrastructure.config;

import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.flashcard.GeneratedCardRepository;
import dev.archsim.domain.flashcard.ReviewScheduleRepository;
import dev.archsim.domain.progress.UserProgressRepository;
import dev.archsim.domain.sprint.SprintRepository;
import dev.archsim.domain.user.UserRepository;
import dev.archsim.infrastructure.persistence.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfig {
    @Bean DomainJson domainJson() { return new DomainJson(); }
    @Bean UserRepository userRepository(UserJpaRepository users) { return new JpaUserRepository(users); }
    @Bean CaseRepository caseRepository(CaseJpaRepository cases, DomainJson json) { return new JpaCaseRepository(cases, json); }
    @Bean AttemptRepository attemptRepository(AttemptJpaRepository attempts, DomainJson json) { return new JpaAttemptRepository(attempts, json); }
    @Bean UserProgressRepository userProgressRepository(ProgressJpaRepository progresses, DomainJson json) { return new JpaUserProgressRepository(progresses, json); }
    @Bean SprintRepository sprintRepository(SprintJpaRepository sprints, DomainJson json) { return new JpaSprintRepository(sprints, json); }
    @Bean GeneratedCardRepository generatedCardRepository(GeneratedCardJpaRepository cards, DomainJson json) { return new JpaGeneratedCardRepository(cards, json); }
    @Bean ReviewScheduleRepository reviewScheduleRepository(ReviewScheduleJpaRepository schedules) { return new JpaReviewScheduleRepository(schedules); }
}
