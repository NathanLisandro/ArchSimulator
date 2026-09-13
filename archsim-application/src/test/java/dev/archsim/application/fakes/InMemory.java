package dev.archsim.application.fakes;

import dev.archsim.application.dto.SqlResult;
import dev.archsim.application.port.*;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.observability.DbState;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.observability.ObservabilityDatasetFactory;
import dev.archsim.domain.observability.CapacityDatasetGenerator;
import dev.archsim.domain.observability.EmptyDatasetGenerator;
import dev.archsim.domain.observability.IncidentDatasetGenerator;
import dev.archsim.domain.observability.SlowQueryDatasetGenerator;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.shared.DomainEvent;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.sql.SqlDialect;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.CaseTemplateRepository;
import dev.archsim.domain.user.PasswordHasher;
import dev.archsim.domain.user.User;
import dev.archsim.domain.user.UserId;
import dev.archsim.domain.user.UserRepository;

import java.time.Instant;
import java.util.*;

/** In-memory fakes for application tests (no Mockito needed for repositories). */
public final class InMemory {
    public static final class Companies implements CompanyRepository {
        public final Map<CompanyId, Company> data = new LinkedHashMap<>();
        public Companies(Company... cs) { for (Company c : cs) data.put(c.id(), c); }
        @Override public Optional<Company> findById(CompanyId id) { return Optional.ofNullable(data.get(id)); }
        @Override public List<Company> findAll() { return List.copyOf(data.values()); }
    }
    public static final class Templates implements CaseTemplateRepository {
        public final List<CaseTemplate> data;
        public Templates(CaseTemplate... ts) { data = List.of(ts); }
        @Override public List<CaseTemplate> findAll() { return data; }
        @Override public Optional<CaseTemplate> findById(String id) { return data.stream().filter(t -> t.id().equals(id)).findFirst(); }
    }
    public static final class Cases implements CaseRepository {
        public final Map<CaseId, Case> data = new HashMap<>();
        @Override public void save(Case c) { data.put(c.id(), c); }
        @Override public Optional<Case> findById(CaseId id) { return Optional.ofNullable(data.get(id)); }
    }
    public static final class Attempts implements AttemptRepository {
        public final Map<AttemptId, Attempt> data = new LinkedHashMap<>();
        @Override public void save(Attempt a) { data.put(a.id(), a); }
        @Override public Optional<Attempt> findById(AttemptId id) { return Optional.ofNullable(data.get(id)); }
        @Override public List<Attempt> findByUser(UserId userId) { return data.values().stream().filter(a -> a.userId().equals(userId)).toList(); }
    }
    public static final class Users implements UserRepository {
        public final Map<UserId, User> data = new HashMap<>();
        @Override public void save(User u) { data.put(u.id(), u); }
        @Override public Optional<User> findById(UserId id) { return Optional.ofNullable(data.get(id)); }
        @Override public Optional<User> findByUsername(String username) { return data.values().stream().filter(u -> u.username().equals(username)).findFirst(); }
    }
    public static final class FixedClock implements Clock {
        public Instant now;
        public FixedClock(Instant now) { this.now = now; }
        @Override public Instant now() { return now; }
    }
    public static final class Seeds implements SeedGenerator {
        public long next = 42;
        @Override public long newSeed() { return next++; }
    }
    public static final class Events implements DomainEventPublisher {
        public final List<DomainEvent> published = new ArrayList<>();
        @Override public void publish(List<DomainEvent> events) { published.addAll(events); }
    }
    public static final class PlainHasher implements PasswordHasher {
        @Override public String hash(String raw) { return "h:" + raw; }
        @Override public boolean matches(String raw, String hash) { return hash.equals("h:" + raw); }
    }
    public static final class Datasets implements ObservabilityDatasetProvider {
        private final CompanyRepository companies;
        private final ObservabilityDatasetFactory factory = new ObservabilityDatasetFactory(List.of(new SlowQueryDatasetGenerator(), new IncidentDatasetGenerator(), new CapacityDatasetGenerator(), new EmptyDatasetGenerator()));
        public Datasets(CompanyRepository companies) { this.companies = companies; }
        @Override public ObservabilityDataset datasetFor(Case c, java.time.Instant recoveredAt) {
            return factory.generate(c, companies.findById(c.companyId()).orElseThrow(), new SeededRandom(c.seed()), recoveredAt);
        }
    }
    public static final class RecordingSandbox implements SqlSandbox {
        public final List<String> executed = new ArrayList<>();
        @Override public SqlResult execute(CaseId caseId, DbState dbState, SqlDialect dialect, String sql) {
            executed.add(sql);
            return new SqlResult(List.of("n"), List.of(List.of(1)), null, null, 1, false);
        }
    }

    public static final class Progresses implements dev.archsim.domain.progress.UserProgressRepository {
        public final Map<UserId, dev.archsim.domain.progress.UserProgress> data = new HashMap<>();
        @Override public void save(dev.archsim.domain.progress.UserProgress p) { data.put(p.userId(), p); }
        @Override public Optional<dev.archsim.domain.progress.UserProgress> findByUser(UserId userId) { return Optional.ofNullable(data.get(userId)); }
    }
    public static final class Sprints implements dev.archsim.domain.sprint.SprintRepository {
        public final Map<dev.archsim.domain.sprint.SprintId, dev.archsim.domain.sprint.Sprint> data = new LinkedHashMap<>();
        @Override public void save(dev.archsim.domain.sprint.Sprint s) { data.put(s.id(), s); }
        @Override public Optional<dev.archsim.domain.sprint.Sprint> findById(dev.archsim.domain.sprint.SprintId id) { return Optional.ofNullable(data.get(id)); }
        @Override public List<dev.archsim.domain.sprint.Sprint> findByUser(UserId userId) {
            return data.values().stream().filter(s -> s.userId().equals(userId)).toList();
        }
        @Override public Optional<dev.archsim.domain.sprint.Sprint> findRunning(UserId userId) {
            return data.values().stream().filter(s -> s.userId().equals(userId))
                    .filter(s -> s.status() == dev.archsim.domain.sprint.SprintStatus.RUNNING).findFirst();
        }
    }
    public static final class Decks implements dev.archsim.domain.flashcard.DeckRepository {
        public final List<dev.archsim.domain.flashcard.Deck> data = new ArrayList<>();
        public void add(dev.archsim.domain.flashcard.Deck deck) { data.add(deck); }
        @Override public List<dev.archsim.domain.flashcard.Deck> findAll() { return List.copyOf(data); }
        @Override public Optional<dev.archsim.domain.flashcard.Deck> findById(String deckId) {
            return data.stream().filter(d -> d.id().equals(deckId)).findFirst();
        }
        @Override public Optional<dev.archsim.domain.flashcard.Flashcard> findCard(dev.archsim.domain.flashcard.FlashcardId id) {
            return data.stream().flatMap(d -> d.cards().stream()).filter(c -> c.id().equals(id)).findFirst();
        }
    }
    public static final class Schedules implements dev.archsim.domain.flashcard.ReviewScheduleRepository {
        public final Map<String, dev.archsim.domain.flashcard.ReviewSchedule> data = new LinkedHashMap<>();
        private static String key(UserId u, dev.archsim.domain.flashcard.FlashcardId c) { return u.value() + "/" + c.value(); }
        @Override public void save(dev.archsim.domain.flashcard.ReviewSchedule s) { data.put(key(s.userId(), s.cardId()), s); }
        @Override public void saveAll(List<dev.archsim.domain.flashcard.ReviewSchedule> list) { list.forEach(this::save); }
        @Override public Optional<dev.archsim.domain.flashcard.ReviewSchedule> find(UserId u, dev.archsim.domain.flashcard.FlashcardId c) {
            return Optional.ofNullable(data.get(key(u, c)));
        }
        @Override public List<dev.archsim.domain.flashcard.ReviewSchedule> findByUser(UserId u) {
            return data.values().stream().filter(s -> s.userId().equals(u)).toList();
        }
    }
    public static final class GeneratedCards implements dev.archsim.domain.flashcard.GeneratedCardRepository {
        public final Map<UserId, List<dev.archsim.domain.flashcard.Flashcard>> data = new LinkedHashMap<>();
        @Override public void saveAll(UserId userId, List<dev.archsim.domain.flashcard.Flashcard> cards) {
            List<dev.archsim.domain.flashcard.Flashcard> existing = data.computeIfAbsent(userId, k -> new ArrayList<>());
            for (dev.archsim.domain.flashcard.Flashcard card : cards) {
                if (existing.stream().noneMatch(c -> c.id().equals(card.id()))) existing.add(card);
            }
        }
        @Override public List<dev.archsim.domain.flashcard.Flashcard> findByUser(UserId userId) { return List.copyOf(data.getOrDefault(userId, List.of())); }
        @Override public Optional<dev.archsim.domain.flashcard.Flashcard> find(UserId userId, dev.archsim.domain.flashcard.FlashcardId id) {
            return data.getOrDefault(userId, List.of()).stream().filter(c -> c.id().equals(id)).findFirst();
        }
    }
}
