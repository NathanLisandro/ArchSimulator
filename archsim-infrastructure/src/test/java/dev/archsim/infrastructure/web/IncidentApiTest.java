package dev.archsim.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.archsim.domain.cases.CaseId;
import dev.archsim.domain.cases.CaseRepository;
import dev.archsim.domain.cases.Mitigation;
import dev.archsim.domain.cases.MitigationEffect;
import dev.archsim.infrastructure.persistence.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Incident flow over HTTP: investigate with logs/traces/timeline, mitigate, communicate, write the post-mortem. */
@SpringBootTest
@AutoConfigureMockMvc
class IncidentApiTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CaseRepository cases;

    /**
     * A mitigação que resolve não é sempre a primeira da lista: em vários templates a opção óbvia é a que
     * só alivia. O efeito é escondido do jogador, então o teste lê do caso salvo em vez de adivinhar pela ordem.
     */
    private String resolvingMitigation(String caseId) {
        return cases.findById(new CaseId(caseId)).orElseThrow().mitigations().stream()
                .filter(m -> m.effect() == MitigationEffect.RESOLVES)
                .map(Mitigation::id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("todo incidente precisa de ao menos uma mitigação que resolve"));
    }

    /** First declared option of a structured field, so the test works for any incident template. */
    private static String firstOption(JsonNode attempt, String fieldId) {
        for (JsonNode field : attempt.at("/theCase/answerForm/fields")) {
            if (field.get("id").asText().equals(fieldId)) return field.at("/options/0/value").asText();
        }
        throw new AssertionError("campo não encontrado: " + fieldId);
    }

    private MockHttpSession login(String username) throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"displayName\":\"Tester\",\"password\":\"secret1\"}")).andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret1\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    @Test
    void investigateMitigateCommunicateAndWriteThePostMortem() throws Exception {
        MockHttpSession s = login("oncall");
        MvcResult started = mvc.perform(post("/api/attempts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"banco-digital\",\"caseType\":\"INCIDENT\",\"level\":\"SENIOR\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode attempt = json.readTree(started.getResponse().getContentAsString());
        String id = attempt.get("id").asText();
        assertThat(attempt.at("/theCase/mitigations")).isNotEmpty();
        assertThat(attempt.at("/theCase/mitigations/0/label").asText()).doesNotContain("{{");
        assertThat(attempt.at("/theCase/mitigations/0").has("effect")).as("verdict/effect ficam escondidos").isFalse();

        mvc.perform(post("/api/attempts/" + id + "/console/logs").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"level=ERROR | stats count by service | sort -count\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("TABLE")).andExpect(jsonPath("$.rows").isNotEmpty());
        mvc.perform(get("/api/attempts/" + id + "/console/traces").session(s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.traces[0].spans").isNotEmpty());
        mvc.perform(get("/api/attempts/" + id + "/console/timeline").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploys").isNotEmpty())
                .andExpect(jsonPath("$.infraResources").isNotEmpty());

        String mitigationId = resolvingMitigation(attempt.at("/theCase/id").asText());
        mvc.perform(post("/api/attempts/" + id + "/mitigations").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitigationId\":\"" + mitigationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effect").value("RESOLVES"))
                .andExpect(jsonPath("$.incidentResolved").value(true))
                .andExpect(jsonPath("$.message").isString());

        mvc.perform(post("/api/attempts/" + id + "/status-updates").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Extrato degradado desde 17:35, rollback em andamento.\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/attempts/" + id + "/status-updates").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Rollback concluído, p99 normalizando.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusUpdates.length()").value(2));

        String postMortem = "## Impacto\\n100% do extrato fora\\n## Linha do tempo\\n17:32 deploy, 17:35 alerta\\n"
                + "## Causa raiz\\nN+1 introduzido no deploy, sem culpado: o pipeline não media queries por request\\n"
                + "## Ações\\nteste de contador de queries e alerta de calls/min";
        String rootCause = firstOption(attempt, "rootCause");
        String pathId = attempt.at("/theCase/solutionPaths/0/id").asText();
        MvcResult submitted = mvc.perform(post("/api/attempts/" + id + "/submit").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"rootCause\":\"" + rootCause + "\",\"solutionPath\":\"" + pathId + "\"},"
                                + "\"markdown\":\"" + postMortem + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = json.readTree(submitted.getResponse().getContentAsString());
        assertThat(result.at("/evaluation/followedPathId").asText()).isEqualTo(pathId);
        assertThat(result.at("/evaluation/totalScore").asDouble()).isGreaterThan(40);
        assertThat(result.at("/evaluation/referenceMarkdown").asText()).contains("## Linha do tempo");
        assertThat(result.at("/resolvedAt").isNull()).isFalse();
        assertThat(result.at("/evaluation/investigationNotes").toString()).contains("MTTM");
    }
}
