package dev.archsim.domain.company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository {
    Optional<Company> findById(CompanyId id);
    List<Company> findAll();
}
