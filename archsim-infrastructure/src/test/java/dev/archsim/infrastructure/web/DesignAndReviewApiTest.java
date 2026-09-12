package dev.archsim.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Design case answered with the drag-and-drop graph, and review case handing over a code artifact. */
@SpringBootTest
@AutoConfigureMockMvc
class DesignAndReviewApiTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpSession login(String username) throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"displayName\":\"Tester\",\"password\":\"secret1\"}")).andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret1\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private JsonNode start(MockHttpSession s, String company, String type, String level) throws Exception {
        MvcResult started = mvc.perform(post("/api/attempts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + company + "\",\"caseType\":\"" + type + "\",\"level\":\"" + level + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(started.getResponse().getContentAsString());
    }

    @Test
    void solutionDesignIsEvaluatedFromTheDrawnGraph() throws Exception {
        MockHttpSession s = login("designer");
        JsonNode attempt = start(s, "ecommerce", "SOLUTION_DESIGN", "SENIOR");
        String id = attempt.get("id").asText();
        assertThat(attempt.at("/theCase/answerForm/fields").toString()).contains("DIAGRAM");

        String body = """
                {
                  "fields": {
                    "solutionPath": "async-reservation",
                    "consistencyModel": "eventual",
                    "idempotencyStrategy": "client-key",
                    "expectedRps": "120",
                    "design": "Publicação por outbox, consumidor idempotente, compensação com aviso ao cliente, contador atômico para hot key, retry com DLQ e timeout."
                  },
                  "markdown": "ver campo design",
                  "graph": {
                    "nodes": [
                      {"id": "chk", "type": "service", "label": "checkout-api", "props": {}},
                      {"id": "topic", "type": "queue", "label": "orders.placed", "props": {"partitions": "36"}},
                      {"id": "inv", "type": "service", "label": "inventory-service", "props": {}},
                      {"id": "odb", "type": "database", "label": "orders-db", "props": {}}
                    ],
                    "edges": [
                      {"from": "chk", "to": "odb", "label": "grava pedido", "async": false, "props": {"pattern": "outbox"}},
                      {"from": "chk", "to": "topic", "label": "OrderPlaced", "async": true, "props": {}},
                      {"from": "topic", "to": "inv", "label": "consome", "async": true, "props": {}}
                    ]
                  }
                }""";
        MvcResult submitted = mvc.perform(post("/api/attempts/" + id + "/submit").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = json.readTree(submitted.getResponse().getContentAsString());

        assertThat(result.at("/evaluation/followedPathId").asText()).isEqualTo("async-reservation");
        JsonNode criteria = result.at("/evaluation/criteria");
        double asyncScore = 0;
        for (JsonNode c : criteria) if (c.get("id").asText().equals("async-edge")) asyncScore = c.get("score").asDouble();
        assertThat(asyncScore).as("fila e ausência de chamada síncrona reconhecidas no desenho").isGreaterThan(0);
        assertThat(result.at("/evaluation/totalScore").asDouble()).isGreaterThan(50);
        assertThat(result.at("/finalAnswer/graph/nodes").size()).isEqualTo(4);
    }

    @Test
    void codeReviewCaseHandsOverTheArtifactToRead() throws Exception {
        MockHttpSession s = login("reviewer");
        JsonNode attempt = start(s, "ecommerce", "CODE_REVIEW", "PLENO");
        assertThat(attempt.at("/theCase/artifacts")).isNotEmpty();
        assertThat(attempt.at("/theCase/artifacts/0/kind").asText()).isEqualTo("CODE");
        assertThat(attempt.at("/theCase/artifacts/0/content").asText()).contains("CheckoutController");
        assertThat(attempt.at("/theCase/artifacts/0/content").asText()).doesNotContain("{{");
    }

    @Test
    void adrCaseOffersNoConsoleAndKeepsTheReferenceHiddenUntilSubmission() throws Exception {
        MockHttpSession s = login("architect");
        JsonNode attempt = start(s, "banco-digital", "ADR", "SENIOR");
        String id = attempt.get("id").asText();
        assertThat(attempt.at("/theCase/tools")).isEmpty();
        assertThat(attempt.toString()).doesNotContain("referenceMarkdown");

        MvcResult submitted = mvc.perform(post("/api/attempts/" + id + "/submit").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"solutionPath\":\"cqrs-projection\",\"consistencyModel\":\"eventual\",\"freshnessTarget\":\"10\","
                                + "\"adr\":\"## Contexto\\nx\\n## Opções consideradas\\nréplica descartada\\n## Decisão\\noutbox e projeção\\n## Consequências\\neventual, reprocessamento, custo\"},"
                                + "\"markdown\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.referenceMarkdown").isString()).andReturn();
        JsonNode result = json.readTree(submitted.getResponse().getContentAsString());
        assertThat(result.at("/evaluation/comparison/message").asText()).isNotBlank();
        assertThat(result.at("/evaluation/criteria").toString()).contains("structure");
    }
}
