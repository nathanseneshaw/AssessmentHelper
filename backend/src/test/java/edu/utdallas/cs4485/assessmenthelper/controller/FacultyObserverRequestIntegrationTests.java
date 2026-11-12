package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.matchOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.newObservation;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withExpiration;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverMatch;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverRequestService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.MediaType;
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
    private static final AccountPrincipal KEIRA = new AccountPrincipal(
            new CurrentUser(4, 41, 1, "Keira Larkspur", "keira.larkspur@example.edu", List.of(Role.PROFESSOR)),
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
        int leandersMatch = db.findAll(ObserverMatch.class).stream()
                .filter(m -> m.signupId() == 33)
                .mapToInt(ObserverMatch::matchId)
                .min()
                .orElseThrow();
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
        setMatchStatus("REJECTED");
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        legacySend(keira).andExpect(status().isConflict());
        setMatchStatus("PROPOSED");
        Observer observer = db.findById(keira, Observer.class);
        db.update(inactive(observer));
        legacySend(keira).andExpect(status().isConflict());
        db.update(observer);
        EvaluationCycle cycle = db.findById(1, EvaluationCycle.class);
        db.update(new EvaluationCycle(
                cycle.cycleId(),
                cycle.semesterId(),
                cycle.signupDeadline(),
                cycle.observationDeadline(),
                cycle.feedbackDeadline(),
                "CLOSED"));
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void rejectsSelfAndDifferentCycleAndInactiveObserverProfessor() throws Exception {
        Observer observer = db.findById(keira, Observer.class);
        db.update(new Observer(observer.observerId(), 31, observer.cycleId(), observer.activeStatus()));
        legacySend(keira).andExpect(status().isConflict());
        // Cycle 9 is the closed Spring 2026 cycle.
        db.update(new Observer(observer.observerId(), 41, 9, observer.activeStatus()));
        legacySend(keira).andExpect(status().isConflict());
        db.update(new Observer(observer.observerId(), 41, 1, observer.activeStatus()));
        db.update(inactive(db.findById(41, Professor.class)));
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
        setSignupStatus("COMPLETED");
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        setSignupStatus("PENDING");
        db.insert(newObservation(signupId, keira, 1, "SCHEDULED"));
        send(AVERY, signupId, matchId).andExpect(status().isConflict());
        assertThat(requestCount()).isZero();
    }

    @Test
    void allowsAnotherCandidateAndKeepsExpiredHistory() throws Exception {
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        legacySend(leander).andExpect(status().isCreated());
        for (var request : requests.findBySignupId(signupId))
            requests.save(withExpiration(request, LocalDateTime.parse("2026-10-20T10:00:00")));
        mvc.perform(as(AVERY, get("/api/professors/31/observer-requests")))
                .andExpect(jsonPath("$[0].requests[0].request.status").value("EXPIRED"));
        legacySend(leander).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(3);
    }

    @Test
    void confirmedRequestRemainsVisibleAndPreventsAnotherRequest() throws Exception {
        send(AVERY, signupId, matchId).andExpect(status().isCreated());
        int requestId = only(requests.findBySignupId(signupId)).requestId();
        mvc.perform(as(
                                new AccountPrincipal(
                                        new CurrentUser(
                                                null,
                                                41,
                                                1,
                                                "Keira Larkspur",
                                                "keira.larkspur@example.edu",
                                                List.of(Role.PROFESSOR)),
                                        null,
                                        true),
                                post("/api/observer-requests/" + requestId + "/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observationDate\":\"2026-11-03\"}"))
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
    void acceptancePreservesOtherRequestsAndAllowsAnotherAcceptance() throws Exception {
        var first = service.createFromMatch(31, signupId, matchId, null);
        var second = service.create(signupId, leander, null);
        var thirdCandidate = service.options(31).getFirst().candidates().stream()
                .filter(candidate -> !candidate.observerId().equals(keira)
                        && !candidate.observerId().equals(leander))
                .findFirst()
                .orElseThrow();
        var third = service.create(signupId, thirdCandidate.observerId(), null);
        service.confirm(first.requestId(), java.time.LocalDate.of(2026, 11, 3));
        assertThat(service.findById(second.requestId())).isEqualTo(second);
        assertThat(service.findById(third.requestId())).isEqualTo(third);
        assertThat(service.confirm(second.requestId(), java.time.LocalDate.of(2026, 11, 3))
                        .status())
                .isEqualTo("CONFIRMED");
        assertThat(service.findById(first.requestId()).status()).isEqualTo("CONFIRMED");
        assertThat(service.findById(third.requestId())).isEqualTo(third);
        assertThat(service.decline(third.requestId()).status()).isEqualTo("DECLINED");
        assertThat(observations.findBySignupIdIn(List.of(signupId))).hasSize(2);
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

    @Test
    void bothRequestEndpointsRejectReinvitingADeclinedObserver() throws Exception {
        var request = service.createFromMatch(31, signupId, matchId, null);
        service.decline(request.requestId());
        send(AVERY, signupId, matchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("This observer has declined this evaluation and cannot be invited again."));
        legacySend(keira).andExpect(status().isConflict());
        assertThat(requestCount()).isEqualTo(1);
        legacySend(leander).andExpect(status().isCreated());
        assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void courseSelectionCannotChangeAfterCandidatesHaveBeenGenerated() throws Exception {
        db.insert(new TeachingHistory(null, 31, 284));
        mvc.perform(as(AVERY, put("/api/professors/31/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284}"))
                .andExpect(status().isConflict());
        assertThat(db.findById(signupId, EvaluationSignup.class).sectionId()).isEqualTo(301);
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
            requests.deleteAll(requests.findBySignupId(signupId));
            db.findAll(ObserverNotification.class).stream()
                    .filter(n -> n.signupId() == signupId
                            && n.createdAt().equals(LocalDateTime.parse("2026-10-21T10:00:00")))
                    .forEach(db::delete);
        }
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
