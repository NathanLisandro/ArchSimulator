package dev.archsim.infrastructure.persistence;

import dev.archsim.domain.attempt.*;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.evaluation.RubricEvaluator;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.user.User;
import dev.archsim.domain.user.UserId;
import dev.archsim.infrastructure.config.PersistenceConfig;
import dev.archsim.infrastructure.content.YamlCaseTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceConfig.class)
class AttemptPersistenceTest extends PostgresTestSupport {
    @Autowired JpaUserRepository users;
    @Autowired JpaCaseRepository cases;
    @Autowired JpaAttemptRepository attempts;

    @Test
    void roundTripsCaseAndAttemptDocuments() {
        Instant t0 = Instant.parse("2026-09-12T15:00:00Z");
        var template = new YamlCaseTemplateRepository(Path.of(System.getProperty("archsim.content", "../content"))).findAll().getFirst();
        Case c = new CaseGenerator().generate(template, Level.PLENO, new SeededRandom(11L), t0);
        User user = new User(new UserId("u-1"), "nathan", "Nathan", "hash", t0);
        users.save(user);
        cases.save(c);

        Attempt a = Attempt.start(AttemptId.of("a-1"), user.id(), c, t0);
        a.answerCheckpoint(c, c.checkpoints().getFirst().id(), c.checkpoints().getFirst().options().getFirst().id(), false, t0);
        a.recordConsoleAction(new ConsoleAction(ConsoleActionKind.EXPLAIN, "ledger_entries", "explain select 1", t0));
        a.submit(c, new FinalAnswer(new StructuredFields(Map.of("solutionPath", c.solutionPaths().getFirst().id(), "rootCause", "MISSING_INDEX")), "## Diagnóstico\nx", null, null), new RubricEvaluator(), t0.plusSeconds(120));
        attempts.save(a);

        Case loadedCase = cases.findById(c.id()).orElseThrow();
        assertThat(loadedCase).isEqualTo(c);

        Attempt loaded = attempts.findById(a.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(loaded.checkpointAnswers()).isEqualTo(a.checkpointAnswers());
        assertThat(loaded.trail()).isEqualTo(a.trail());
        assertThat(loaded.finalAnswer()).isEqualTo(a.finalAnswer());
        assertThat(loaded.evaluation()).isEqualTo(a.evaluation());
        assertThat(loaded.durationSeconds()).isEqualTo(120);
        assertThat(attempts.findByUser(user.id())).hasSize(1);
        assertThat(users.findByUsername("nathan")).isPresent();
    }
}
