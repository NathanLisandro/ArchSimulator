package dev.archsim.application.usecase;

import dev.archsim.application.dto.CatalogEntry;
import dev.archsim.application.fakes.InMemory;
import dev.archsim.application.fakes.TestTemplates;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O catálogo existe para a tela inicial não oferecer uma combinação que não tem template:
 * escolher empresa + tipo + nível e receber 404 é o pior jeito de descobrir que o conteúdo não existe.
 */
class ListCatalogTest {
    @Test
    void listsWhichCombinationsHaveContent() {
        ListCatalog listCatalog = new ListCatalog(new InMemory.Templates(TestTemplates.slowQuery(Level.JUNIOR, Level.SENIOR)));

        List<CatalogEntry> entries = listCatalog.all();

        assertThat(entries).hasSize(1);
        CatalogEntry entry = entries.get(0);
        assertThat(entry.companyId()).isEqualTo("banco-digital");
        assertThat(entry.caseType()).isEqualTo(CaseType.SLOW_QUERY);
        assertThat(entry.levels()).containsExactly(Level.JUNIOR, Level.SENIOR);
        assertThat(entry.templates()).isEqualTo(1);
    }

    @Test
    void mergesLevelsOfTemplatesThatShareCompanyAndType() {
        ListCatalog listCatalog = new ListCatalog(new InMemory.Templates(
                TestTemplates.slowQuery(Level.JUNIOR, Level.SENIOR),
                TestTemplates.slowQuery(Level.PLENO, Level.STAFF)));

        List<CatalogEntry> entries = listCatalog.all();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).levels()).containsExactly(Level.JUNIOR, Level.PLENO, Level.SENIOR, Level.STAFF);
        assertThat(entries.get(0).templates()).isEqualTo(2);
    }

    @Test
    void separatesEntriesByCompanyAndType() {
        ListCatalog listCatalog = new ListCatalog(new InMemory.Templates(
                TestTemplates.slowQuery(Level.PLENO),
                TestTemplates.incident(Level.SENIOR)));

        assertThat(listCatalog.all())
                .extracting(CatalogEntry::caseType)
                .containsExactly(CaseType.SLOW_QUERY, CaseType.INCIDENT);
    }
}
