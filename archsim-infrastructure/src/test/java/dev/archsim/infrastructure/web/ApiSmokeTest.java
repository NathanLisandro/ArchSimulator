package dev.archsim.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.archsim.infrastructure.persistence.PostgresTestSupport;
import jakarta.servlet.http.HttpSession;
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

/** End-to-end through HTTP: register → login → start case → checkpoint → console → submit. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullSlowQueryFlow() throws Exception {
        mvc.perform(get("/api/attempts/x")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nathan\",\"displayName\":\"Nathan\",\"password\":\"secret1\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("nathan"));
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"nathan\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk()).andReturn();
        HttpSession session = login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        MockHttpSession s = (MockHttpSession) session;

        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Nathan"));
        mvc.perform(get("/api/companies")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("banco-digital"));

        MvcResult started = mvc.perform(post("/api/attempts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"banco-digital\",\"caseType\":\"SLOW_QUERY\",\"level\":\"PLENO\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode attempt = json.readTree(started.getResponse().getContentAsString());
        String id = attempt.get("id").asText();
        String checkpointId = attempt.at("/theCase/checkpoints/0/id").asText();
        String optionId = attempt.at("/theCase/checkpoints/0/options/0/id").asText();
        assertThat(attempt.at("/theCase/checkpoints/0/answer").isMissingNode() || attempt.at("/theCase/checkpoints/0/answer").isNull()).isTrue();
        String table = attempt.at("/theCase/answerForm/fields").findValues("id").contains(json.getNodeFactory().textNode("slowQueryTable")) ? "ledger_entries" : "ledger_entries";

        mvc.perform(post("/api/attempts/" + id + "/checkpoints/" + checkpointId).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"" + optionId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verdict").isString()).andExpect(jsonPath("$.message").isString());

        mvc.perform(post("/api/attempts/" + id + "/console/sql").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"select query, mean_exec_time from pg_stat_statements order by mean_exec_time desc limit 3\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0][1]").isNumber());
        mvc.perform(post("/api/attempts/" + id + "/console/sql").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"explain select * from " + table + " where account_id = 1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planText").isString());
        mvc.perform(get("/api/attempts/" + id + "/console/metrics").session(s).param("service", "core-db").param("metric", "cpu_utilization"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series[0].points").isArray());

        // incident console: logs, traces, timeline
        mvc.perform(post("/api/attempts/" + id + "/console/logs").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"| stats count by service\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("TABLE"));
        mvc.perform(get("/api/attempts/" + id + "/console/timeline").session(s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deploys").isArray());

        MvcResult submitted = mvc.perform(post("/api/attempts/" + id + "/submit").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"rootCause\":\"MISSING_INDEX\",\"solutionPath\":\"" + attempt.at("/theCase/solutionPaths/0/id").asText() + "\"},\"markdown\":\"## Diagnóstico\\nSeq scan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED")).andReturn();
        JsonNode result = json.readTree(submitted.getResponse().getContentAsString());
        assertThat(result.at("/evaluation/totalScore").asDouble()).isBetween(0.0, 100.0);
        assertThat(result.at("/evaluation/criteria").size()).isGreaterThan(2);
        assertThat(result.at("/evaluation/referenceMarkdown").asText()).contains("## Diagnóstico");
        assertThat(result.at("/evaluation/comparison/message").asText()).isNotBlank();
        assertThat(result.at("/trail").size()).isEqualTo(5);
    }
}
