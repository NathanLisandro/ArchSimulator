package dev.archsim.application.dto;

import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;

import java.util.List;

/**
 * Uma combinação empresa + tipo de caso que tem conteúdo, com os níveis em que ela existe.
 * A tela inicial usa isso para não oferecer uma escolha que terminaria em 404.
 */
public record CatalogEntry(String companyId, CaseType caseType, List<Level> levels, int templates) {}
