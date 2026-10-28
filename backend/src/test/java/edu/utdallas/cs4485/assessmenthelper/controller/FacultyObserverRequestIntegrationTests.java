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

    // From data.sql. Kaia is on the Assessment Committee. The committee has sent Avery's Fall 2026 list, whose
    // proposed observers are Leander, Keira, Xiomara and Perrin, and Avery has not asked anyone yet. Hollis has no
    // signup.
    private static final AccountPrincipal KAIA = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal HOLLIS = new AccountPrincipal(
            new CurrentUser(null, 9, 1, "Hollis Carrowmore", "hollis.carrowmore@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal AVERY = new AccountPrincipal(
            new CurrentUser(null, 31, 1, "Avery Hazelwood", "avery.hazelwood@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    private int signupId;
    private int matchId;
    private int keira;
    private int leander;

    @BeforeEach
    void demoSignup() {
        signupId = db.queryForObject("""
                SELECT s.signup_id FROM evaluation_signup s JOIN observee e ON e.observee_id = s.observee_id
                WHERE e.professor_id = 31 AND e.cycle_id = 1
                """, Integer.class);
        keira = observer(41);
        leander = observer(13);
        matchId = db.queryForObject(
                "SELECT match_id FROM observer_match WHERE signup_id = ? AND observer_id = ?",
                Integer.class,
                signupId,
                keira);
    }

    @Test
    void sendsFromMatchAndRestoresRequestWithNamesDatesAndNoDuplicateOnRetry() throws Exception {
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signupId").value(signupId))
                .andExpect(jsonPath("$[0].candidates.length()").value(4))
                .andExpect(jsonPath("$[0].candidates[0].observerName").value("Leander Coldwell"))
                .andExpect(jsonPath("$[0].requests.length()").value(0));
        var first = send(AVERY, signupId, matchId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestDate").value("2026-10-21T10:00:00"))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-28T10:00:00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var retry = send(AVERY, signupId, matchId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(retry).isEqualTo(first);
        assertThat(requestCount()).isEqualTo(1);
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("PENDING"))
                .andExpect(jsonPath("$[0].requests[0].observerName").value("Keira Larkspur"));
    }

    @Test
    void rejectsMissingOrForeignSignupAndMatchWithoutCreatingRequests() throws Exception {
        int leandersMatch =
                db.queryForObject("SELECT MIN(match_id) FROM observer_match WHERE signup_id = 33", Integer.class);
        send(HOLLIS, signupId, matchId).andExpect(status().isNotFound());
        send(AVERY, signupId, leandersMatch).andExpect(status().isBadRequest());
        send(AVERY, signupId, 99999).andExpect(status().isNotFound());
        send(AVERY, 99999, matchId).andExpect(status().isNotFound());
        mvc.perform(as(AVERY, post("/api/professors/31/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        // Ids of other professors, known or not, are refused before any lookup.
        mvc.perform(as(AVERY, get("/api/professors/99999/observer-requests"))).andExpect(status().isForbidden());
        assertThat(requestCount()).isZero();
    }

    @Test
    void onlyTheProfessorCanSendTheirRequestsButCommitteeMembersCanReadThem() throws Exception {
        mvc.perform(as(HOLLIS, get("/api/professors/31/observer-requests"))).andExpect(status().isForbidden());
        mvc.perform(as(HOLLIS, post("/api/professors/31/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/professors/31/observer-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signupId").value(signupId));
        mvc.perform(as(KAIA, post("/api/professors/31/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId + "}"))
                .andExpect(status().isForbidden());
        assertThat(requestCount()).isZero();
    }

    @Test
    void legacyEndpointAlsoRequiresAnActiveProposedMatchAndOpenSignup() throws Exception {
        db.update("UPDATE observer_match SET match_status = 'REJECTED' WHERE match_id = ?", matchId);
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        legacySend(keira).andExpect(status().isConflict());
        db.update("UPDATE observer_match SET match_status = 'PROPOSED' WHERE match_id = ?", matchId);
        db.update("UPDATE observer SET active_status = FALSE WHERE observer_id = ?", keira);
        legacySend(keira).andExpect(status().isConflict());
        db.update("UPDATE observer SET active_status = TRUE WHERE observer_id = ?", keira);
        db.update("UPDATE evaluation_cycle SET status = 'CLOSED' WHERE cycle_id = 1");
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void rejectsSelfAndDifferentCycleAndInactiveObserverProfessor() throws Exception {
        db.update("UPDATE observer SET professor_id = 31 WHERE observer_id = ?", keira);
        legacySend(keira).andExpect(status().isConflict());
        // Cycle 9 is the closed Spring 2026 cycle.
        db.update("UPDATE observer SET professor_id = 41, cycle_id = 9 WHERE observer_id = ?", keira);
        legacySend(keira).andExpect(status().isConflict());
        db.update("UPDATE observer SET cycle_id = 1 WHERE observer_id = ?", keira);
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id = 41");
        legacySend(keira).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void rejectsScheduledAndCompletedSignupsAndPastExpiration() throws Exception {
        mvc.perform(as(AVERY, post("/api/professors/31/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + matchId
                                + ",\"expirationDate\":\"2026-10-20T10:00:00\"}"))
                .andExpect(status().isBadRequest());
        db.update("UPDATE evaluation_signup SET status = 'COMPLETED' WHERE signup_id = ?", signupId);
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        db.update("UPDATE evaluation_signup SET status = 'PENDING' WHERE signup_id = ?", signupId);
        db.update(
                "INSERT INTO observation (signup_id, observer_id, template_id, status) VALUES (?, ?, 1, 'SCHEDULED')",
                signupId,
                keira);
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void allowsAnotherCandidateAndKeepsExpiredHistory() throws Exception {
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        legacySend(leander).andExpect(status().isCreated());
        db.update(
                "UPDATE observer_request SET expiration_date = TIMESTAMP '2026-10-20 10:00:00' WHERE signup_id = ?",
                signupId);
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("EXPIRED"));
        legacySend(leander).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(3);
    }

    @Test
    void confirmedRequestRemainsVisibleAndPreventsAnotherRequest() throws Exception {
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        int requestId = db.queryForObject(
                "SELECT request_id FROM observer_request WHERE signup_id = ?", Integer.class, signupId);
        mvc.perform(as(KAIA, post("/api/observer-requests/" + requestId + "/confirm")))
                .andExpect(status().isOk());
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].requests[0].request.responseDate").value("2026-10-21T10:00:00"))
                .andExpect(jsonPath("$[0].unavailableReason")
                        .value("An observation is already scheduled for this signup."));
        // Accepting recorded the signup's observation, so even the same request cannot be sent again.
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void acceptanceExpiresOtherPendingRequests() throws Exception {
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        legacySend(leander).andExpect(status().isCreated());
        int acceptedId = db.queryForObject(
                "SELECT request_id FROM observer_request WHERE signup_id=? AND observer_id=?",
                Integer.class,
                signupId,
                keira);
        service.confirm(acceptedId);
        assertThat(db.queryForObject(
                        "SELECT status FROM observer_request WHERE signup_id=? AND observer_id=?",
                        String.class,
                        signupId,
                        leander))
                .isEqualTo("EXPIRED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, signupId))
                .isEqualTo(1);
    }

    @Test
    void fivePendingRequestsBlockSixthButAllowRetry() throws Exception {
        // Populate four pending requests to exercise the limit independently of generated pool size.
        db.update("""
                INSERT INTO observer_request(signup_id,observer_id,request_date,expiration_date,status)
                SELECT ?,observer_id,TIMESTAMP '2026-10-21 10:00:00',TIMESTAMP '2026-10-28 10:00:00','PENDING'
                FROM observer WHERE observer_id NOT IN (?,?) ORDER BY observer_id LIMIT 4
                """, signupId, keira, leander);
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(5);
        legacySend(leander).andExpect(status().isConflict());
        assertThat(requestCount()).isEqualTo(5);
    }

    @Test
    void courseSelectionCannotChangeAfterCandidatesHaveBeenGenerated() throws Exception {
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (31, 284)");
        mvc.perform(as(AVERY, put("/api/professors/31/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject(
                        "SELECT section_id FROM evaluation_signup WHERE signup_id = ?", Integer.class, signupId))
                .isEqualTo(301);
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
                return service.createFromMatch(31, signupId, matchId, null);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return service.createFromMatch(31, signupId, matchId, null);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).requestId())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).requestId());
            assertThat(requestCount()).isEqualTo(1);
        } finally {
            // This test commits, so remove the request and the notification it sent to keep data.sql's records.
            db.update("DELETE FROM observer_request WHERE signup_id = ?", signupId);
            db.update(
                    "DELETE FROM observer_notification WHERE signup_id = ? AND created_at = TIMESTAMP '2026-10-21 10:00:00'",
                    signupId);
        }
    }

    private int observer(int professorId) {
        return db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id = ? AND cycle_id = 1", Integer.class, professorId);
    }

    private ResultActions send(AccountPrincipal professor, int signup, int match) throws Exception {
        return mvc.perform(
                as(professor, post("/api/professors/" + professor.user().professorId() + "/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signup + ",\"matchId\":" + match + "}"));
    }

    private ResultActions legacySend(int observer) throws Exception {
        return mvc.perform(as(AVERY, post("/api/observer-requests"))
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
