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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Progression, sprint, study mode and export, over HTTP against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
class ProgressStudyApiTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpSession login(String username) throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"displayName\":\"Tester\",\"password\":\"secret1\"}")).andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret1\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String solveOneCase(MockHttpSession s) throws Exception {
        MvcResult started = mvc.perform(post("/api/attempts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"banco-digital\",\"caseType\":\"ADR\",\"level\":\"SENIOR\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode attempt = json.readTree(started.getResponse().getContentAsString());
        String id = attempt.get("id").asText();
        String pathId = attempt.at("/theCase/solutionPaths/0/id").asText();
        mvc.perform(post("/api/attempts/" + id + "/submit").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":{\"solutionPath\":\"" + pathId + "\",\"consistencyModel\":\"eventual\",\"freshnessTarget\":\"10\","
                                + "\"adr\":\"## Contexto\\nx\\n## Opções consideradas\\nréplica descartada\\n## Decisão\\noutbox\\n## Consequências\\neventual, reprocessamento, custo, lag\"},"
                                + "\"markdown\":\"\"}"))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void solvingACaseUpdatesProgressHistoryAndGeneratesCards() throws Exception {
        MockHttpSession s = login("student");
        mvc.perform(get("/api/progress").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value("JUNIOR"))
                .andExpect(jsonPath("$.solvedCount").value(0));

        String attemptId = solveOneCase(s);

        MvcResult progress = mvc.perform(get("/api/progress").session(s)).andExpect(status().isOk()).andReturn();
        JsonNode view = json.readTree(progress.getResponse().getContentAsString());
        assertThat(view.get("solvedCount").asInt()).isEqualTo(1);
        assertThat(view.get("averageScore").asDouble()).isGreaterThan(0);
        assertThat(view.at("/byType/ADR/attempts").asInt()).isEqualTo(1);
        assertThat(view.get("missingForNextRank").asText()).contains("pleno");
        assertThat(view.at("/recentAttempts/0/caseTitle").asText()).isNotBlank();

        mvc.perform(get("/api/attempts").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].score").isNumber());

        mvc.perform(get("/api/attempts/" + attemptId + "/export").session(s))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".md")))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("## Avaliação"));
    }

    @Test
    void sprintTracksCasesAgainstTheClock() throws Exception {
        MockHttpSession s = login("sprinter");
        MvcResult started = mvc.perform(post("/api/sprints").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCases\":2,\"minutes\":45,\"level\":\"SENIOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.remainingSeconds").value(org.hamcrest.Matchers.greaterThan(2600)))
                .andReturn();
        String sprintId = json.readTree(started.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/sprints/current").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(sprintId));

        solveOneCase(s);
        mvc.perform(get("/api/sprints/" + sprintId).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCases").value(1))
                .andExpect(jsonPath("$.remainingCases").value(1));

        solveOneCase(s);
        mvc.perform(get("/api/sprints/" + sprintId).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.results.length()").value(2));
        mvc.perform(get("/api/sprints/current").session(s)).andExpect(status().isNoContent());
    }

    @Test
    void studyModeServesSeedDecksAndSchedulesReviews() throws Exception {
        MockHttpSession s = login("reviewer2");
        MvcResult decks = mvc.perform(get("/api/study/decks").session(s)).andExpect(status().isOk()).andReturn();
        JsonNode list = json.readTree(decks.getResponse().getContentAsString());
        assertThat(list.size()).isGreaterThanOrEqualTo(5);
        String deckId = list.get(0).get("id").asText();
        assertThat(list.get(0).get("dueCount").asInt()).isGreaterThan(0);

        MvcResult next = mvc.perform(get("/api/study/decks/" + deckId + "/next").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.front").isString())
                .andExpect(jsonPath("$.back").isString())
                .andReturn();
        String cardId = json.readTree(next.getResponse().getContentAsString()).get("cardId").asText();

        mvc.perform(post("/api/study/cards/" + cardId + "/review").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"GOOD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.dueAt").isString());

        MvcResult after = mvc.perform(get("/api/study/decks").session(s)).andExpect(status().isOk()).andReturn();
        JsonNode deck = json.readTree(after.getResponse().getContentAsString()).get(0);
        assertThat(deck.get("dueCount").asInt()).isLessThan(list.get(0).get("dueCount").asInt());
    }

    @Test
    void solvedCasesAddGeneratedCardsToTheDecks() throws Exception {
        MockHttpSession s = login("cardmaker");
        MvcResult before = mvc.perform(get("/api/study/decks").session(s)).andExpect(status().isOk()).andReturn();
        long generatedBefore = totalGenerated(json.readTree(before.getResponse().getContentAsString()));

        solveOneCase(s);

        MvcResult after = mvc.perform(get("/api/study/decks").session(s)).andExpect(status().isOk()).andReturn();
        assertThat(totalGenerated(json.readTree(after.getResponse().getContentAsString()))).isGreaterThan(generatedBefore);
    }

    private static long totalGenerated(JsonNode decks) {
        long total = 0;
        for (JsonNode deck : decks) total += deck.get("generatedCards").asLong();
        return total;
    }
}
