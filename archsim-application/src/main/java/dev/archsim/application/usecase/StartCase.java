package dev.archsim.application.usecase;

import dev.archsim.application.NotFoundException;
import dev.archsim.application.dto.AttemptView;
import dev.archsim.application.port.SeedGenerator;
import dev.archsim.domain.attempt.Attempt;
import dev.archsim.domain.attempt.AttemptId;
import dev.archsim.domain.attempt.AttemptRepository;
import dev.archsim.domain.cases.Case;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.cases.CaseType;
import dev.archsim.domain.cases.Level;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.company.CompanyRepository;
import dev.archsim.domain.shared.Clock;
import dev.archsim.domain.shared.SeededRandom;
import dev.archsim.domain.template.CaseGenerator;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.CaseTemplateRepository;
import dev.archsim.domain.user.UserId;

import java.util.List;

/** "Próximo caso": picks a template matching company/type/level (random when omitted), generates the case and opens an attempt. */
public class StartCase {
    public record Command(UserId userId, String companyId, String caseType, Level level, String templateId) {
        public Command(UserId userId, String companyId, String caseType, Level level) {
            this(userId, companyId, caseType, level, null);
        }
    }

    private final CompanyRepository companies;
    private final CaseTemplateRepository templates;
    private final CaseRepository cases;
    private final AttemptRepository attempts;
    private final CaseGenerator generator;
    private final SeedGenerator seeds;
    private final Clock clock;

    public StartCase(CompanyRepository companies, CaseTemplateRepository templates, CaseRepository cases, AttemptRepository attempts,
                     CaseGenerator generator, SeedGenerator seeds, Clock clock) {
        this.companies = companies; this.templates = templates; this.cases = cases; this.attempts = attempts;
        this.generator = generator; this.seeds = seeds; this.clock = clock;
    }

    public AttemptView execute(Command cmd) {
        long seed = seeds.newSeed();
        SeededRandom random = new SeededRandom(seed);
        Level level = cmd.level() == null ? Level.JUNIOR : cmd.level();
        CompanyId companyId = cmd.companyId() != null ? new CompanyId(cmd.companyId()) : random.fork("company").pick(companies.findAll()).id();
        CaseType type = cmd.caseType() == null ? null : CaseType.valueOf(cmd.caseType());

        CaseTemplate template;
        if (cmd.templateId() != null) {
            // caso de acompanhamento: o template é escolhido, e a empresa vem dele
            template = templates.findById(cmd.templateId())
                    .orElseThrow(() -> new NotFoundException("Template não encontrado: " + cmd.templateId()));
            if (!template.supports(level)) level = template.levels().getFirst();
            companyId = template.companyId();
        } else {
            List<CaseTemplate> candidates = templates.find(companyId, type, level);
            if (candidates.isEmpty()) throw new NotFoundException("Nenhum template para empresa=" + companyId.value() + " tipo=" + type + " nível=" + level);
            template = random.fork("template").pick(candidates);
        }
        CompanyId resolvedCompany = companyId;
        companies.findById(resolvedCompany).orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + resolvedCompany.value()));

        Case theCase = generator.generate(template, level, random, clock.now());
        cases.save(theCase);
        Attempt attempt = Attempt.start(AttemptId.random(), cmd.userId(), theCase, clock.now());
        attempts.save(attempt);
        return AttemptView.from(attempt, theCase);
    }
}
