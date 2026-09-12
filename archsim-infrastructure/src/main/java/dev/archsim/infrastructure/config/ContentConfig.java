package dev.archsim.infrastructure.config;

import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.flashcard.DeckRepository;
import dev.archsim.domain.template.CaseTemplateRepository;
import dev.archsim.infrastructure.content.YamlCaseTemplateRepository;
import dev.archsim.infrastructure.content.YamlCompanyRepository;
import dev.archsim.infrastructure.content.YamlDeckRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ContentConfig {
    @Bean CompanyRepository companyRepository(@Value("${archsim.content-path}") String path) { return new YamlCompanyRepository(Path.of(path)); }
    @Bean CaseTemplateRepository caseTemplateRepository(@Value("${archsim.content-path}") String path) { return new YamlCaseTemplateRepository(Path.of(path)); }
    @Bean DeckRepository deckRepository(@Value("${archsim.content-path}") String path) { return new YamlDeckRepository(Path.of(path)); }
}
