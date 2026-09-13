package dev.archsim.infrastructure;

import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.domain.company.Company;
import dev.archsim.domain.observability.ObservabilityDataset;
import dev.archsim.domain.observability.ObservabilityDatasetFactory;
import dev.archsim.domain.observability.CapacityDatasetGenerator;
import dev.archsim.domain.observability.EmptyDatasetGenerator;
import dev.archsim.domain.observability.IncidentDatasetGenerator;
import dev.archsim.domain.observability.SlowQueryDatasetGenerator;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.CaseTemplateRepository;
import dev.archsim.infrastructure.content.YamlCaseTemplateRepository;
import dev.archsim.infrastructure.content.YamlCompanyRepository;
import dev.archsim.infrastructure.content.YamlDeckRepository;
import dev.archsim.domain.flashcard.Deck;
import dev.archsim.domain.flashcard.Flashcard;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the real content/ directory and generates cases from every template with several seeds and levels: content lint. */
class ContentValidationTest {
    static final Path CONTENT = Path.of(System.getProperty("archsim.content", "../content"));
    private final YamlCompanyRepository companies = new YamlCompanyRepository(CONTENT);
    private final YamlCaseTemplateRepository templates = new YamlCaseTemplateRepository(CONTENT);
    private final CaseGenerator generator = new CaseGenerator();
    private final ObservabilityDatasetFactory datasets = new ObservabilityDatasetFactory(List.of(new SlowQueryDatasetGenerator(), new IncidentDatasetGenerator(), new CapacityDatasetGenerator(), new EmptyDatasetGenerator()));

    @Test
    void companiesLoad() {
        assertThat(companies.findAll()).isNotEmpty();
        Company bank = companies.findAll().getFirst();
        assertThat(bank.schema().tables()).isNotEmpty();
        assertThat(bank.architecture().services()).isNotEmpty();
    }

    @Test
    void seedDecksLoadWithUniqueCardsOnBothSides() {
        List<Deck> decks = new YamlDeckRepository(CONTENT).findAll();
        assertThat(decks).hasSizeGreaterThanOrEqualTo(5);
        List<Flashcard> cards = decks.stream().flatMap(d -> d.cards().stream()).toList();
        assertThat(cards).hasSizeGreaterThanOrEqualTo(100);
        assertThat(cards.stream().map(c -> c.id().value()).distinct()).hasSameSizeAs(cards);
        assertThat(cards).allSatisfy(c -> {
            assertThat(c.front()).as(c.id().value()).isNotBlank().doesNotContain("{{");
            assertThat(c.back()).as(c.id().value()).isNotBlank().doesNotContain("{{");
            assertThat(c.competencies()).as(c.id().value() + " sem competência").isNotEmpty();
            assertThat(c.generated()).isFalse();
        });
        assertThat(decks).allSatisfy(d -> assertThat(d.cards()).as(d.id()).hasSizeGreaterThanOrEqualTo(10));
    }

    @Test
    void everyTemplateGeneratesCoherentCasesAndDatasets() {
        assertThat(templates.findAll()).isNotEmpty();
        for (CaseTemplate t : templates.findAll()) {
            Company company = companies.findById(t.companyId()).orElseThrow(() -> new AssertionError(t.id() + ": empresa desconhecida " + t.companyId().value()));
            for (long seed = 1; seed <= 15; seed++) {
                for (Level level : t.levels()) {
                    Case c = generator.generate(t, level, new SeededRandom(seed), Instant.parse("2026-09-12T12:00:00Z"));
                    assertThat(c.narrative().symptom()).as(t.id()).doesNotContain("{{");
                    assertThat(c.referenceAnswer().markdown()).as(t.id()).doesNotContain("{{");
                    c.checkpoints().forEach(cp -> cp.options().forEach(o -> assertThat(o.feedback()).as(t.id() + "/" + cp.id()).doesNotContain("{{")));
                    c.solutionPaths().forEach(p -> p.rubric().criteria().forEach(cr -> assertThat(cr.feedbackWhenMissing()).as(t.id()).doesNotContain("{{")));
                    // trade-off cases are a forced choice between two viable paths; every other type offers at least three
                    int minPaths = c.type() == dev.archsim.domain.cases.CaseType.TRADE_OFF ? 2 : 3;
                    assertThat(c.solutionPaths()).as(t.id()).hasSizeGreaterThanOrEqualTo(minPaths);
                    assertThat(c.checkpoints()).as(t.id()).hasSizeGreaterThanOrEqualTo(2);
                    c.answerForm().fields().stream().filter(f -> f.id().equals(c.answerForm().pathFieldId())).findFirst()
                            .ifPresent(f -> assertThat(f.options()).extracting("value").containsExactlyInAnyOrderElementsOf(c.solutionPaths().stream().map(p -> p.id()).toList()));
                    c.mitigations().forEach(m -> {
                        assertThat(m.label()).as(t.id()).doesNotContain("{{");
                        assertThat(m.feedbackWhenInformed()).as(t.id()).doesNotContain("{{");
                        assertThat(m.feedbackWhenBlind()).as(t.id()).doesNotContain("{{");
                    });
                    c.stakeholderMessages().forEach(sm -> assertThat(sm.text()).as(t.id()).doesNotContain("{{"));
                    c.artifacts().forEach(art -> assertThat(art.content()).as(t.id()).doesNotContain("{{"));
                    c.followUps().forEach(followUp -> {
                        assertThat(followUp.teaser()).as(t.id()).doesNotContain("{{");
                        assertThat(templates.findById(followUp.templateId())).as(t.id() + ": follow-up aponta para template inexistente")
                                .isPresent();
                        assertThat(c.solutionPath(followUp.pathId())).as(t.id() + ": follow-up aponta para caminho inexistente")
                                .isPresent();
                    });
                    if (!c.scenario().kind().equals("none")) {
                        ObservabilityDataset ds = datasets.generate(c, company, new SeededRandom(c.seed()));
                        assertThat(ds.metrics()).as(t.id()).isNotEmpty();
                        String table = c.scenario().paramOr("table", null);
                        if (table != null) assertThat(company.table(table)).as(t.id() + ": tabela do cenário existe").isPresent();
                        assertThat(company.service(c.scenario().param("service"))).as(t.id() + ": serviço do cenário existe").isPresent();
                        if (c.tools().contains(dev.archsim.domain.console.ConsoleTool.LOGS)) assertThat(ds.logs()).as(t.id() + ": console de logs sem dados").isNotEmpty();
                        if (c.tools().contains(dev.archsim.domain.console.ConsoleTool.TRACES)) assertThat(ds.traces()).as(t.id() + ": console de traces sem dados").isNotEmpty();
                        if (c.tools().contains(dev.archsim.domain.console.ConsoleTool.DEPLOYS)) assertThat(ds.deploys()).as(t.id() + ": linha do tempo sem deploys").isNotEmpty();
                    }
                }
            }
        }
    }

    /**
     * Todo incidente precisa de uma saída. Sem nenhuma mitigação que resolve o jogador fica preso, e a ordem
     * não pode ser a dica: em vários templates a opção óbvia é a que só alivia, e é assim de propósito.
     */
    @Test
    void everyIncidentHasAWayOut() {
        CaseTemplateRepository templates = new YamlCaseTemplateRepository(Path.of("..", "content"));
        List<CaseTemplate> incidents = templates.findAll().stream().filter(t -> t.type() == CaseType.INCIDENT).toList();

        assertThat(incidents).isNotEmpty();
        assertThat(incidents).allSatisfy(t ->
                assertThat(t.mitigations())
                        .as(t.id() + ": nenhuma mitigação com efeito RESOLVES")
                        .anyMatch(m -> m.effect() == MitigationEffect.RESOLVES));
    }
}
