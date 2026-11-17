package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.matchOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FacultyObserverRequestIntegrationTests.FixedTime.class)
@Transactional
class FacultyObserverRequestIntegrationTests {

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
    private JdbcAggregateTemplate db;

    @Autowired
    private ObserverRequestRepository requests;

    @Autowired
    private ObservationRepository observations;

    private int signupId;
    private int matchId;
    private int keira;
    private int leander;

    @BeforeEach
    void demoSignup() {
        signupId = signupOf(db, 31);
        keira = observerOf(db, 41);
        leander = observerOf(db, 13);
        matchId = matchOf(db, signupId, 41);
    }

    @Test
    void sendsFromMatchAndReturnsTheSameRequestOnRetry() throws Exception {
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signupId").value(signupId))
                .andExpect(jsonPath("$[0].candidates.length()").value(4));
        var first = send(AVERY, signupId, matchId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
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
                .andExpect(jsonPath("$[0].requests[0].observerName").value("Keira Larkspur"));
    }

    @Test
    void rejectsAMatchFromAnotherSignupWithoutCreatingARequest() throws Exception {
        int leandersMatch = db.findAll(ObserverMatch.class).stream()
                .filter(m -> m.signupId() == 33)
                .mapToInt(ObserverMatch::matchId)
                .min()
                .orElseThrow();
        send(AVERY, signupId, leandersMatch).andExpect(status().isBadRequest());
        assertThat(requestCount()).isZero();
    }

    @Test
    void onlyTheProfessorCanSendTheirRequestsButCommitteeMembersCanReadThem() throws Exception {
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
    void fivePendingRequestsBlockSixthButAllowRetry() throws Exception {
        // Populate four pending requests to exercise the limit independently of generated pool size.
        db.findAll(Observer.class).stream()
                .map(Observer::observerId)
                .filter(id -> id != keira && id != leander)
                .sorted()
                .limit(4)
                .forEach(id -> db.insert(new ObserverRequest(
                        null,
                        signupId,
                        id,
                        LocalDateTime.parse("2026-10-21T10:00:00"),
                        LocalDateTime.parse("2026-10-28T10:00:00"),
                        null,
                        "PENDING",
                        false)));
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(5);
        legacySend(leander).andExpect(status().isConflict());
        assertThat(requestCount()).isEqualTo(5);
    }

    private void setMatchStatus(String status) {
        ObserverMatch match = db.findById(matchId, ObserverMatch.class);
        db.update(new ObserverMatch(
                match.matchId(), match.signupId(), match.observerId(), match.generatedDate(), status));
    }

    private void setSignupStatus(String status) {
        db.update(withStatus(db.findById(signupId, EvaluationSignup.class), status));
    }

    private ResultActions send(AccountPrincipal professor, int signup, int match) throws Exception {
        return mvc.perform(
                as(professor, post("/api/professors/" + professor.user().professorId() + "/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signup + ",\"matchId\":" + match + "}"));
    }

    private ResultActions legacySend(int observer) throws Exception {
        return mvc.perform(as(KAIA, post("/api/observer-requests"))
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
        return requests.findBySignupId(signupId).size();
    }
}
