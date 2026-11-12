package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.cycleOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** The feedback survey over HTTP: listing, submitting, and who is refused. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProcessFeedbackIntegrationTests.FixedTime.class)
@Transactional
class ProcessFeedbackIntegrationTests {

    // The day after Fall 2026's observation deadline (2026-11-15), well before its feedback deadline (2026-12-01).
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock feedbackClock() {
            return Clock.fixed(Instant.parse("2026-11-16T16:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    private static final String ANSWERS = "{\"ratings\":[4,5,3,4,2,4],\"comments\":\"Clear steps.\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    // From data.sql: Keira and Leander are observees in Fall 2026, and Keira was also observed in Fall 2025. Dario has
    // never been an observee.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal dario;
    private int fall2026;
    private int fall2025;

    @BeforeEach
    void observees() {
        keira = principal(db, "keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal(db, "leander.coldwell@example.edu", 1, Role.PROFESSOR);
        dario = principal(db, "dario.hollowell@example.edu", 1, Role.PROFESSOR);
        fall2026 = cycleOf(db, "Fall", 2026);
        fall2025 = cycleOf(db, "Fall", 2025);
    }

    @Test
    void observeeListsTheirSurveysNewestFirst() throws Exception {
        mvc.perform(get("/api/feedback-surveys").with(user(keira)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cycleId").value(contains(fall2026, fall2025)))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].status").value("CLOSED"));
    }

    @Test
    void observeeSubmitsOnce() throws Exception {
        submit(leander, fall2026, ANSWERS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.ratings").value(contains(4, 5, 3, 4, 2, 4)));

        submit(leander, fall2026, ANSWERS).andExpect(status().isConflict());
    }

    @Test
    void otherProfessorsAndAnonymousUsersAreRefused() throws Exception {
        submit(dario, fall2026, ANSWERS).andExpect(status().isForbidden());
        mvc.perform(get("/api/feedback-surveys")).andExpect(status().isUnauthorized());
    }

    // Adds a CSRF token the way the frontend gets one (see FacultyObserverRequestIntegrationTests for why the csrf()
    // helper is not used).
    private ResultActions submit(AccountPrincipal principal, int cycleId, String body) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(post("/api/feedback-surveys/" + cycleId)
                .with(user(principal))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
