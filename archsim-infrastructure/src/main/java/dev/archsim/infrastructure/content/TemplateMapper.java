package dev.archsim.infrastructure.content;

import com.fasterxml.jackson.databind.JsonNode;
import dev.archsim.domain.cases.*;
import dev.archsim.domain.company.CompanyId;
import dev.archsim.domain.console.ConsoleActionKind;
import dev.archsim.domain.console.ConsoleTool;
import dev.archsim.domain.rubric.*;
import dev.archsim.domain.template.CaseTemplate;
import dev.archsim.domain.template.ParameterSpec;
import dev.archsim.domain.template.RecommendationRule;

import java.util.*;

import static dev.archsim.infrastructure.content.Yaml.*;

final class TemplateMapper {
    CaseTemplate map(JsonNode n, String where) {
        String id = req(n, "id", where);
        String w = where + " [" + id + "]";
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        JsonNode p = n.get("parameters");
        if (p != null) p.fields().forEachRemaining(e -> params.put(e.getKey(), parameter(e.getValue(), w + " parameters." + e.getKey())));
        Map<String, String> derived = new LinkedHashMap<>();
        JsonNode d = n.get("derived");
        if (d != null) d.fields().forEachRemaining(e -> derived.put(e.getKey(), e.getValue().asText()));

        JsonNode nar = n.get("narrative");
        if (nar == null) throw new ContentException(w + ": 'narrative' é obrigatório");
        Narrative narrative = new Narrative(req(nar, "title", w), req(nar, "symptom", w), opt(nar, "businessContext", ""), strings(nar, "hints"));

        List<Checkpoint> checkpoints = list(n, "checkpoints").stream().map(c -> new Checkpoint(req(c, "id", w), req(c, "question", w),
                list(c, "options").stream().map(o -> new CheckpointOption(req(o, "id", w), req(o, "label", w),
                        Verdict.valueOf(opt(o, "verdict", "GOOD").toUpperCase()), req(o, "feedback", w))).toList())).toList();

        List<SolutionPath> paths = list(n, "solutionPaths").stream().map(sp -> new SolutionPath(req(sp, "id", w), req(sp, "name", w), opt(sp, "summary", ""),
                strings(sp, "pros"), strings(sp, "cons"), opt(sp, "bestWhen", ""), rubric(sp.get("rubric"), w + " path " + req(sp, "id", w)))).toList();
        if (paths.isEmpty()) throw new ContentException(w + ": pelo menos um solutionPath");

        JsonNode form = n.get("answerForm");
        AnswerForm answerForm = new AnswerForm(list(form, "fields").stream().map(f -> new FieldSpec(req(f, "id", w), req(f, "label", w),
                FieldType.valueOf(opt(f, "type", "TEXT").toUpperCase()),
                list(f, "options").stream().map(o -> new FieldOption(req(o, "value", w), opt(o, "label", req(o, "value", w)))).toList(),
                optBool(f, "required", false), opt(f, "help", null))).toList(), opt(form, "pathField", "solutionPath"));

        JsonNode sc = n.get("scenario");
        ScenarioSpec scenario = sc == null ? new ScenarioSpec("none", Map.of()) : new ScenarioSpec(req(sc, "kind", w), stringMap(sc.get("params")));

        JsonNode rec = n.get("recommendation");
        String defaultPath = rec == null ? paths.getFirst().id() : opt(rec, "default", paths.getFirst().id());
        List<RecommendationRule> rules = list(rec, "rules").stream().map(r -> new RecommendationRule(req(r, "param", w), opt(r, "op", ">"),
                Double.parseDouble(opt(r, "value", "0")), req(r, "path", w), req(r, "reason", w))).toList();

        List<Mitigation> mitigations = list(n, "mitigations").stream().map(m -> new Mitigation(req(m, "id", w), req(m, "label", w),
                opt(m, "description", ""), Verdict.valueOf(opt(m, "verdict", "RISKY").toUpperCase()),
                MitigationEffect.valueOf(opt(m, "effect", "NONE").toUpperCase()), optBool(m, "requiresDiagnosis", false),
                req(m, "feedback", w), opt(m, "feedbackWhenBlind", req(m, "feedback", w)))).toList();

        List<StakeholderMessage> stakeholders = list(n, "stakeholders").stream().map(sm -> new StakeholderMessage(req(sm, "id", w),
                req(sm, "from", w), optLong(sm, "afterSeconds", 300), req(sm, "text", w), optBool(sm, "expectsUpdate", true))).toList();

        List<CaseArtifact> artifacts = list(n, "artifacts").stream().map(a -> new CaseArtifact(req(a, "id", w), req(a, "title", w),
                ArtifactKind.valueOf(opt(a, "kind", "CODE").toUpperCase()), opt(a, "language", "text"), req(a, "content", w))).toList();

        Set<ConsoleTool> tools = new LinkedHashSet<>();
        strings(n, "tools").forEach(t -> tools.add(ConsoleTool.valueOf(t.toUpperCase())));
        List<Level> levels = strings(n, "levels").stream().map(l -> Level.valueOf(l.toUpperCase())).toList();
        if (levels.isEmpty()) levels = List.of(Level.values());

        return new CaseTemplate(id, CaseType.valueOf(req(n, "type", w).toUpperCase()), new CompanyId(req(n, "company", w)), levels,
                strings(n, "competencies"), params, derived, narrative, checkpoints, paths, answerForm, opt(n, "reference", ""), scenario,
                defaultPath, rules, tools, mitigations, stakeholders, artifacts);
    }

    private ParameterSpec parameter(JsonNode v, String w) {
        if (v.has("range")) {
            JsonNode r = v.get("range");
            return new ParameterSpec.IntRange(r.get(0).asLong(), r.get(1).asLong(), optLong(v, "step", 1));
        }
        if (v.has("choice")) return new ParameterSpec.Choice(strings(v, "choice"));
        if (v.has("group")) {
            List<Map<String, String>> options = new ArrayList<>();
            v.get("group").forEach(o -> options.add(stringMap(o)));
            return new ParameterSpec.Group(options);
        }
        throw new ContentException(w + ": parâmetro precisa de range, choice ou group");
    }

    private Rubric rubric(JsonNode r, String w) {
        if (r == null) throw new ContentException(w + ": 'rubric' é obrigatória");
        return new Rubric(list(r, "criteria").stream().map(c -> new Criterion(req(c, "id", w), req(c, "name", w), optInt(c, "weight", 10),
                list(c, "evidence").stream().map(e -> evidence(e, w)).toList(), opt(c, "missing", "Faltou: " + req(c, "name", w)),
                opt(c, "present", req(c, "name", w) + " ✔"))).toList());
    }

    private Evidence evidence(JsonNode e, String w) {
        String type = opt(e, "type", "keyword").toLowerCase();
        return switch (type) {
            case "keyword" -> new KeywordEvidence(opt(e, "field", null), strings(e, "anyOf"), optInt(e, "min", 1));
            case "regex" -> new RegexEvidence(opt(e, "field", null), req(e, "pattern", w));
            case "section" -> new SectionEvidence(strings(e, "headings"));
            case "choice" -> new ChoiceEvidence(req(e, "field", w), strings(e, "anyOf"));
            case "investigation" -> new InvestigationEvidence(ConsoleActionKind.valueOf(req(e, "kind", w).toUpperCase()), opt(e, "target", null), optInt(e, "min", 1));
            case "mitigation" -> new MitigationEvidence(opt(e, "id", null),
                    e.get("effect") == null ? null : MitigationEffect.valueOf(req(e, "effect", w).toUpperCase()), optBool(e, "informed", false));
            case "status_update", "communication" -> new StatusUpdateEvidence(optInt(e, "min", 1), optLong(e, "firstWithin", 0));
            case "graph" -> new GraphEvidence(GraphEvidence.Kind.valueOf(req(e, "check", w).toUpperCase()),
                    opt(e, "value", null), opt(e, "from", null), opt(e, "to", null), optInt(e, "min", 1));
            case "number" -> new NumberEvidence(req(e, "field", w), req(e, "expected", w), Double.parseDouble(opt(e, "tolerance", "0.25")));
            default -> throw new ContentException(w + ": tipo de evidência desconhecido '" + type + "'");
        };
    }
}
