package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.CompanyView;
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
    public CompanyController(ListCompanies listCompanies) { this.listCompanies = listCompanies; }

    @GetMapping("/companies") public List<CompanyView> all() { return listCompanies.all(); }
    @GetMapping("/companies/{id}") public CompanyView byId(@PathVariable String id) { return listCompanies.byId(id); }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
}
