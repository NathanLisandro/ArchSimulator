package dev.archsim.domain.template;

import dev.archsim.domain.cases.*;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.shared.RandomSource;
import dev.archsim.domain.shared.TemplateRenderer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Domain service: samples template parameters with a seeded random source, derives values, renders every text and
 * builds an immutable Case. Same template + seed + level + now ⇒ identical case.
 */
public final class CaseGenerator {
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneOffset.ofHours(-3));
    private final TemplateRenderer renderer = new TemplateRenderer();

    public Case generate(CaseTemplate template, Level level, RandomSource random, Instant now) {
        Map<String, String> params = sample(template, random.fork("params"), now);
        PathRecommendation recommendation = recommend(template, params);
        List<String> hints = level.showsHints() ? render(template.narrative().hints(), params) : List.of();
        Narrative narrative = new Narrative(r(template.narrative().title(), params), r(template.narrative().symptom(), params),
                r(template.narrative().businessContext(), params), hints);
        List<Checkpoint> checkpoints = template.checkpoints().stream().map(cp -> new Checkpoint(cp.id(), r(cp.question(), params),
                cp.options().stream().map(o -> new CheckpointOption(o.id(), r(o.label(), params), o.verdict(), r(o.feedback(), params))).toList())).toList();
        List<SolutionPath> paths = template.solutionPaths().stream().map(p -> new SolutionPath(p.id(), r(p.name(), params), r(p.summary(), params),
                render(p.pros(), params), render(p.cons(), params), r(p.bestWhen(), params), renderRubric(p.rubric(), params))).toList();
        AnswerForm form = new AnswerForm(template.answerForm().fields().stream().map(f -> new FieldSpec(f.id(), r(f.label(), params), f.type(),
                f.options().stream().map(o -> new FieldOption(o.value(), r(o.label(), params))).toList(), f.required(), r(f.help(), params))).toList(),
                template.answerForm().pathFieldId());
        List<Mitigation> mitigations = template.mitigations().stream().map(m -> new Mitigation(m.id(), r(m.label(), params),
                r(m.description(), params), m.verdict(), m.effect(), m.requiresDiagnosis(),
                r(m.feedbackWhenInformed(), params), r(m.feedbackWhenBlind(), params))).toList();
        List<StakeholderMessage> messages = template.stakeholderMessages().stream().map(sm -> new StakeholderMessage(sm.id(),
                r(sm.from(), params), sm.atElapsedSeconds(), r(sm.text(), params), sm.expectsUpdate())).toList();
        List<CaseArtifact> artifacts = template.artifacts().stream().map(a -> new CaseArtifact(a.id(), r(a.title(), params),
                a.kind(), a.language(), r(a.content(), params))).toList();
        List<FollowUp> followUps = template.followUps().stream()
                .map(f -> new FollowUp(f.pathId(), f.templateId(), r(f.teaser(), params))).toList();
        Map<String, String> scenarioParams = new LinkedHashMap<>();
        template.scenario().params().forEach((k, v) -> scenarioParams.put(k, r(v, params)));
        return new Case(CaseId.random(), template.companyId(), template.id(), template.type(), level, random.seed(), narrative, checkpoints, paths, form,
                new ReferenceAnswer(r(template.referenceMarkdown(), params)), new ScenarioSpec(template.scenario().kind(), scenarioParams),
                recommendation, template.competencies(), template.tools(), Map.copyOf(params), now, mitigations, messages, artifacts, followUps);
    }

    private Map<String, String> sample(CaseTemplate template, RandomSource random, Instant now) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("now", now.toString());
        params.put("nowLabel", LABEL.format(now));
        template.parameters().forEach((name, spec) -> {
            switch (spec) {
                case ParameterSpec.IntRange r -> {
                    long steps = (r.max() - r.min()) / r.step();
                    params.put(name, String.valueOf(r.min() + random.nextLong(0, steps) * r.step()));
                }
                case ParameterSpec.Choice c -> params.put(name, random.pick(c.values()));
                case ParameterSpec.Group g -> params.putAll(random.pick(g.options()));
            }
        });
        resolveNestedPlaceholders(params);
        template.derived().forEach((name, expr) -> params.put(name, String.valueOf(ArithmeticExpression.evaluate(expr, params))));
        // Time helpers: any *HoursAgo / *MinutesAgo parameter gets *At (ISO instant) and *Label (dd/MM HH:mm, America/Sao_Paulo).
        for (Map.Entry<String, String> e : List.copyOf(params.entrySet())) {
            String k = e.getKey();
            Instant at = null;
            if (k.endsWith("HoursAgo")) at = now.minusSeconds(Long.parseLong(e.getValue()) * 3600);
            else if (k.endsWith("MinutesAgo")) at = now.minusSeconds(Long.parseLong(e.getValue()) * 60);
            if (at != null) {
                params.put(k + "At", at.toString());
                params.put(k + "Label", LABEL.format(at));
                String base = k.replaceAll("(Hours|Minutes)Ago$", "");
                params.putIfAbsent(base + "At", at.toString());
                params.putIfAbsent(base + "Label", LABEL.format(at));
            }
        }
        return params;
    }

    /** Parameter values may themselves contain {{placeholders}} (common inside group options); resolve them before use. */
    private void resolveNestedPlaceholders(Map<String, String> params) {
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            for (Map.Entry<String, String> e : List.copyOf(params.entrySet())) {
                String value = e.getValue();
                if (value == null || !value.contains("{{")) continue;
                String rendered = renderer.render(value, params);
                if (!rendered.equals(value)) { params.put(e.getKey(), rendered); changed = true; }
            }
            if (!changed) return;
        }
    }

    private PathRecommendation recommend(CaseTemplate template, Map<String, String> params) {
        for (RecommendationRule rule : template.rules()) {
            if (rule.applies(params.get(rule.param()))) return new PathRecommendation(rule.pathId(), r(rule.reason(), params));
        }
        SolutionPath def = template.solutionPaths().stream().filter(p -> p.id().equals(template.defaultPathId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Default path not found: " + template.defaultPathId()));
        return new PathRecommendation(def.id(), r(def.bestWhen(), params));
    }

    private Rubric renderRubric(Rubric rubric, Map<String, String> params) {
        return new Rubric(rubric.criteria().stream().map(c -> new Criterion(c.id(), r(c.name(), params), c.weight(),
                c.evidence().stream().map(e -> renderEvidence(e, params)).toList(),
                r(c.feedbackWhenMissing(), params), r(c.feedbackWhenPresent(), params))).toList());
    }

    private Evidence renderEvidence(Evidence e, Map<String, String> params) {
        return switch (e) {
            case KeywordEvidence k -> new KeywordEvidence(k.field(), render(k.anyOf(), params), k.minMatches());
            case RegexEvidence rx -> new RegexEvidence(rx.field(), r(rx.pattern(), params));
            case ChoiceEvidence c -> new ChoiceEvidence(c.field(), render(c.anyOf(), params));
            case SectionEvidence s -> s;
            case InvestigationEvidence i -> new InvestigationEvidence(i.kind(), r(i.targetContains(), params), i.minCount());
            case MitigationEvidence m -> m;
            case StatusUpdateEvidence u -> u;
            case GraphEvidence g -> new GraphEvidence(g.kind(), r(g.value(), params), r(g.from(), params), r(g.to(), params), g.minCount());
            // expected may be a parameter expression ("peakOrdersPerSecond * 2"); resolve it to a literal now
            case NumberEvidence n -> new NumberEvidence(n.field(),
                    String.valueOf(ArithmeticExpression.evaluateDouble(n.expected(), params)), n.relativeTolerance());
        };
    }

    private String r(String text, Map<String, String> params) { return text == null ? null : renderer.render(text, params); }
    private List<String> render(List<String> texts, Map<String, String> params) {
        return texts == null ? List.of() : texts.stream().map(t -> r(t, params)).toList();
    }
}
