package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.CatalogEntry;
import dev.archsim.application.dto.CompanyView;
import dev.archsim.application.usecase.ListCatalog;
import dev.archsim.application.usecase.ListCompanies;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CompanyController {
    private final ListCompanies listCompanies;
    private final ListCatalog listCatalog;
    public CompanyController(ListCompanies listCompanies, ListCatalog listCatalog) {
        this.listCompanies = listCompanies;
        this.listCatalog = listCatalog;
    }

    @GetMapping("/companies") public List<CompanyView> all() { return listCompanies.all(); }
    @GetMapping("/companies/{id}") public CompanyView byId(@PathVariable String id) { return listCompanies.byId(id); }
    /** Quais combinações de empresa, tipo e nível têm conteúdo. A tela inicial só oferece o que existe. */
    @GetMapping("/catalog") public List<CatalogEntry> catalog() { return listCatalog.all(); }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
}
