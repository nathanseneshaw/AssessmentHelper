package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverRequestService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FacultyObserverRequestIntegrationTests.FixedTime.class)
@Transactional
class FacultyObserverRequestIntegrationTests {
    @Autowired
    private ObserverRequestService service;

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock requestClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    // Professors from test-data.sql. Alice is on the Assessment Committee and is the observer in Dave's match.
    private static final AccountPrincipal ALICE = new AccountPrincipal(
            new CurrentUser(1, 1, 1, "Alice Alice", "alice@utdallas.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal CAROL = new AccountPrincipal(
            new CurrentUser(3, 3, 1, "Carol Carol", "carol@utdallas.edu", List.of(Role.PROFESSOR)), null, true);
    private static final AccountPrincipal DAVE = new AccountPrincipal(
            new CurrentUser(4, 4, 1, "Dave Dave", "dave@utdallas.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    private int signupId;
    private int matchId;

    @BeforeEach
    void demoSignup() {
        signupId = db.queryForObject("""
                SELECT s.signup_id FROM evaluation_signup s JOIN observee e ON e.observee_id = s.observee_id
                WHERE e.professor_id = 4 AND e.cycle_id = 1
                """, Integer.class);
        matchId = db.queryForObject("SELECT match_id FROM observer_match WHERE signup_id = ?", Integer.class, signupId);
    }

    @Test
    void sendsFromMatchAndRestoresRequestWithNamesDatesAndNoDuplicateOnRetry() throws Exception {
        mvc.perform(as(DAVE, get("/api/professors/4/observer-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signupId").value(signupId))
                .andExpect(jsonPath("$[0].candidates[0].observerName").value("Alice Alice"))
                .andExpect(jsonPath("$[0].requests.length()").value(0));
        var first = send(DAVE, signupId, matchId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestDate").value("2026-10-21T10:00:00"))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-28T10:00:00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var retry = send(DAVE, signupId, matchId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(retry).isEqualTo(first);
        assertThat(requestCount()).isEqualTo(1);
        mvc.perform(as(DAVE, get("/api/professors/4/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("PENDING"))
                .andExpect(jsonPath("$[0].requests[0].observerName").value("Alice Alice"));
    }

    @Test
    void rejectsMissingOrForeignSignupAndMatchWithoutCreatingRequests() throws Exception {
        send(CAROL, signupId, matchId).andExpect(status().isNotFound());
        send(DAVE, signupId, 1).andExpect(status().isBadRequest());
        send(DAVE, signupId, 99999).andExpect(status().isNotFound());
        send(DAVE, 99999, matchId).andExpect(status().isNotFound());
        mvc.perform(as(DAVE, post("/api/professors/4/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        // Ids of other professors, known or not, are refused before any lookup.
        mvc.perform(as(DAVE, get("/api/professors/99999/observer-requests"))).andExpect(status().isForbidden());
        assertThat(requestCount()).isZero();
    }

    @Test
    void onlyTheProfessorCanSendTheirRequestsButCommitteeMembersCanReadThem() throws Exception {
        mvc.perform(as(CAROL, get("/api/professors/4/observer-requests"))).andExpect(status().isForbidden());
        mvc.perform(as(CAROL, post("/api/professors/4/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(ALICE, get("/api/professors/4/observer-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signupId").value(signupId));
        mvc.perform(as(ALICE, post("/api/professors/4/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId + "}"))
                .andExpect(status().isForbidden());
        assertThat(requestCount()).isZero();
    }

    @Test
    void legacyEndpointAlsoRequiresAnActiveProposedMatchAndOpenSignup() throws Exception {
        db.update("UPDATE observer_match SET match_status = 'REJECTED' WHERE match_id = ?", matchId);
        send(DAVE, signupId, matchId).andExpect(status().isConflict());
        legacySend(1).andExpect(status().isConflict());
        db.update("UPDATE observer_match SET match_status = 'PROPOSED' WHERE match_id = ?", matchId);
        db.update("UPDATE observer SET active_status = FALSE WHERE observer_id = 1");
        legacySend(1).andExpect(status().isConflict());
        db.update("UPDATE observer SET active_status = TRUE WHERE observer_id = 1");
        db.update("UPDATE evaluation_cycle SET status = 'CLOSED' WHERE cycle_id = 1");
        send(DAVE, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void rejectsSelfAndDifferentCycleAndInactiveObserverProfessor() throws Exception {
        db.update("UPDATE observer SET professor_id = 4 WHERE observer_id = 1");
        legacySend(1).andExpect(status().isConflict());
        db.update("UPDATE observer SET professor_id = 1, cycle_id = 2 WHERE observer_id = 1");
        legacySend(1).andExpect(status().isConflict());
        db.update("UPDATE observer SET cycle_id = 1 WHERE observer_id = 1");
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id = 1");
        legacySend(1).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void rejectsScheduledAndCompletedSignupsAndPastExpiration() throws Exception {
        mvc.perform(as(DAVE, post("/api/professors/4/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId
                                + ",\"expirationDate\":\"2026-10-20T10:00:00\"}"))
                .andExpect(status().isBadRequest());
        db.update("UPDATE evaluation_signup SET status = 'COMPLETED' WHERE signup_id = ?", signupId);
        send(DAVE, signupId, matchId).andExpect(status().isConflict());
        db.update("UPDATE evaluation_signup SET status = 'PENDING' WHERE signup_id = ?", signupId);
        db.update(
                "INSERT INTO observation (signup_id, observer_id, template_id, status) VALUES (?, 1, 1, 'SCHEDULED')",
                signupId);
        send(DAVE, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void blocksAnotherCandidateUntilExpirationAndKeepsExpiredHistory() throws Exception {
        db.update("INSERT INTO observer (professor_id, cycle_id, active_status) VALUES (3, 1, TRUE)");
        int other = db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id = 3 AND cycle_id = 1", Integer.class);
        db.update(
                "INSERT INTO observer_match (signup_id, observer_id, match_status) VALUES (?, ?, 'PROPOSED')",
                signupId,
                other);
        send(DAVE, signupId, matchId).andExpect(status().isCreated());
        legacySend(other).andExpect(status().isConflict());
        db.update(
                "UPDATE observer_request SET expiration_date = TIMESTAMP '2026-10-20 10:00:00' WHERE signup_id = ?",
                signupId);
        mvc.perform(as(DAVE, get("/api/professors/4/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("EXPIRED"));
        legacySend(other).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void confirmedRequestRemainsVisibleAndPreventsAnotherRequest() throws Exception {
        send(DAVE, signupId, matchId).andExpect(status().isCreated());
        int requestId = db.queryForObject(
                "SELECT request_id FROM observer_request WHERE signup_id = ?", Integer.class, signupId);
        mvc.perform(as(ALICE, post("/api/observer-requests/" + requestId + "/confirm")))
                .andExpect(status().isOk());
        mvc.perform(as(DAVE, get("/api/professors/4/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].requests[0].request.responseDate").value("2026-10-21T10:00:00"));
        send(DAVE, signupId, matchId).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void courseSelectionCannotChangeAfterCandidatesHaveBeenGenerated() throws Exception {
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (4, 9)");
        mvc.perform(as(DAVE, put("/api/professors/4/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9}"))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject(
                        "SELECT section_id FROM evaluation_signup WHERE signup_id = ?", Integer.class, signupId))
                .isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousRetriesCreateExactlyOneRequest() throws Exception {
        assertThat(requestCount()).isZero();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return service.createFromMatch(4, signupId, matchId, null);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return service.createFromMatch(4, signupId, matchId, null);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).requestId())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).requestId());
            assertThat(requestCount()).isEqualTo(1);
        } finally {
            db.update("DELETE FROM observer_request WHERE signup_id = ?", signupId);
        }
    }

    private ResultActions send(AccountPrincipal professor, int signup, int match) throws Exception {
        return mvc.perform(
                as(professor, post("/api/professors/" + professor.user().professorId() + "/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signup + ",\"matchId\":" + match + "}"));
    }

    private ResultActions legacySend(int observer) throws Exception {
        return mvc.perform(as(DAVE, post("/api/observer-requests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupId\":" + signupId + ",\"observerId\":" + observer + "}"));
    }

    // Signs the request in as the professor and adds a CSRF token the way the frontend gets one. The csrf() helper
    // is not used because it changes the shared Spring context (see AuthenticationTests).
    private MockHttpServletRequestBuilder as(AccountPrincipal professor, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(professor)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private int requestCount() {
        return db.queryForObject("SELECT COUNT(*) FROM observer_request WHERE signup_id = ?", Integer.class, signupId);
    }
}
