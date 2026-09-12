package dev.archsim.infrastructure.config;

import dev.archsim.application.port.DomainEventPublisher;
import dev.archsim.application.port.ObservabilityDatasetProvider;
import dev.archsim.application.port.SeedGenerator;
import dev.archsim.application.port.SqlSandbox;
import dev.archsim.application.usecase.*;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.evaluation.CaseEvaluator;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.export.MarkdownExporter;
import dev.archsim.domain.flashcard.DeckRepository;
import dev.archsim.domain.flashcard.FlashcardFactory;
import dev.archsim.domain.flashcard.GeneratedCardRepository;
import dev.archsim.domain.flashcard.ReviewScheduleRepository;
import dev.archsim.domain.progress.UserProgressRepository;
import dev.archsim.domain.sprint.SprintRepository;
import dev.archsim.domain.observability.CapacityDatasetGenerator;
import dev.archsim.domain.observability.EmptyDatasetGenerator;
import dev.archsim.domain.observability.IncidentDatasetGenerator;
import dev.archsim.domain.observability.ObservabilityDatasetFactory;
import dev.archsim.domain.observability.SlowQueryDatasetGenerator;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.sql.ExplainSimulator;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.template.CaseTemplateRepository;
import dev.archsim.domain.user.PasswordHasher;
import dev.archsim.domain.user.UserRepository;
import dev.archsim.infrastructure.sandbox.H2SqlSandbox;
import dev.archsim.infrastructure.security.BCryptPasswordHasher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Configuration
public class UseCaseConfig {
    @Bean Clock clock() { return Instant::now; }
    @Bean SeedGenerator seedGenerator() { SecureRandom r = new SecureRandom(); return () -> Math.abs(r.nextLong()); }
    @Bean PasswordHasher passwordHasher() { return new BCryptPasswordHasher(); }
    @Bean CaseGenerator caseGenerator() { return new CaseGenerator(); }
    @Bean ExplainSimulator explainSimulator() { return new ExplainSimulator(); }
    @Bean CaseEvaluator caseEvaluator() { return new RubricEvaluator(); }
    @Bean ObservabilityDatasetFactory datasetFactory() { return new ObservabilityDatasetFactory(List.of(new SlowQueryDatasetGenerator(), new IncidentDatasetGenerator(), new CapacityDatasetGenerator(), new EmptyDatasetGenerator())); }
    @Bean ObservabilityDatasetProvider datasetProvider(ObservabilityDatasetFactory f, CompanyRepository companies) { return new CachingDatasetProvider(f, companies); }
    @Bean SqlSandbox sqlSandbox() { return new H2SqlSandbox(); }
    @Bean DomainEventPublisher domainEventPublisher(ApplicationEventPublisher spring) { return events -> events.forEach(spring::publishEvent); }

    @Bean RegisterUser registerUser(UserRepository users, PasswordHasher hasher, Clock clock) { return new RegisterUser(users, hasher, clock); }
    @Bean AuthenticateUser authenticateUser(UserRepository users, PasswordHasher hasher) { return new AuthenticateUser(users, hasher); }
    @Bean ListCompanies listCompanies(CompanyRepository companies) { return new ListCompanies(companies); }
    @Bean StartCase startCase(CompanyRepository c, CaseTemplateRepository t, CaseRepository cases, AttemptRepository a, CaseGenerator g, SeedGenerator s, Clock clock) {
        return new StartCase(c, t, cases, a, g, s, clock);
    }
    @Bean GetAttempt getAttempt(AttemptRepository a, CaseRepository c, Clock clock) { return new GetAttempt(a, c, clock); }
    @Bean AnswerCheckpoint answerCheckpoint(AttemptRepository a, CaseRepository c, Clock clock) { return new AnswerCheckpoint(a, c, clock); }
    @Bean RunSql runSql(AttemptRepository a, CaseRepository c, CompanyRepository co, ObservabilityDatasetProvider d, SqlSandbox s, ExplainSimulator e, Clock clock) {
        return new RunSql(a, c, co, d, s, e, clock);
    }
    @Bean QueryMetrics queryMetrics(AttemptRepository a, CaseRepository c, ObservabilityDatasetProvider d, Clock clock) { return new QueryMetrics(a, c, d, clock); }
    @Bean SearchLogs searchLogs(AttemptRepository a, CaseRepository c, ObservabilityDatasetProvider d, Clock clock) { return new SearchLogs(a, c, d, clock); }
    @Bean GetTraces getTraces(AttemptRepository a, CaseRepository c, ObservabilityDatasetProvider d, Clock clock) { return new GetTraces(a, c, d, clock); }
    @Bean GetTimeline getTimeline(AttemptRepository a, CaseRepository c, CompanyRepository co, ObservabilityDatasetProvider d, Clock clock) {
        return new GetTimeline(a, c, co, d, clock);
    }
    @Bean ApplyMitigation applyMitigation(AttemptRepository a, CaseRepository c, Clock clock) { return new ApplyMitigation(a, c, clock); }
    @Bean PostStatusUpdate postStatusUpdate(AttemptRepository a, CaseRepository c, Clock clock) { return new PostStatusUpdate(a, c, clock); }
    @Bean FlashcardFactory flashcardFactory() { return new FlashcardFactory(); }
    @Bean MarkdownExporter markdownExporter() { return new MarkdownExporter(); }
    @Bean CompleteAttempt completeAttempt(UserProgressRepository progresses, SprintRepository sprints, GeneratedCardRepository cards,
                                          FlashcardFactory factory, DomainEventPublisher events) {
        return new CompleteAttempt(progresses, sprints, cards, factory, events);
    }
    @Bean SubmitAnswer submitAnswer(AttemptRepository a, CaseRepository c, CaseEvaluator e, DomainEventPublisher p, Clock clock, CompleteAttempt complete) {
        return new SubmitAnswer(a, c, e, p, clock, complete);
    }
    @Bean GetProgress getProgress(UserProgressRepository progresses, AttemptRepository a, CaseRepository c, Clock clock) {
        return new GetProgress(progresses, a, c, clock);
    }
    @Bean ListAttempts listAttempts(AttemptRepository a, CaseRepository c) { return new ListAttempts(a, c); }
    @Bean StartSprint startSprint(SprintRepository sprints, Clock clock) { return new StartSprint(sprints, clock); }
    @Bean GetSprint getSprint(SprintRepository sprints, Clock clock) { return new GetSprint(sprints, clock); }
    @Bean StudyDecks studyDecks(DeckRepository decks, GeneratedCardRepository cards, ReviewScheduleRepository schedules, Clock clock) {
        return new StudyDecks(decks, cards, schedules, clock);
    }
    @Bean ExportAttempt exportAttempt(AttemptRepository a, CaseRepository c, MarkdownExporter exporter) { return new ExportAttempt(a, c, exporter); }
}
