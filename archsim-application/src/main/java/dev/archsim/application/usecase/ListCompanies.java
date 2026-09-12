package dev.archsim.application.usecase;

import dev.archsim.application.NotFoundException;
import dev.archsim.application.dto.CompanyView;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.company.CompanyRepository;

import java.util.List;

public class ListCompanies {
    private final CompanyRepository companies;
    public ListCompanies(CompanyRepository companies) { this.companies = companies; }

    public List<CompanyView> all() { return companies.findAll().stream().map(CompanyView::from).toList(); }

    public CompanyView byId(String id) {
        return companies.findById(new CompanyId(id)).map(CompanyView::from).orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + id));
    }
}
