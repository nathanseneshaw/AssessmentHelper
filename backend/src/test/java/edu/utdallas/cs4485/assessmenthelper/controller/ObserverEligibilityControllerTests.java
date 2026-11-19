package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.matchOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.professor;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withReleasedAt;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withSignupDeadline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationDeferral;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverMatchRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ObserverEligibilityControllerTests.FixedTime.class)
@Transactional
class ObserverEligibilityControllerTests {

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock eligibilityClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    // The demo accounts from data.sql, plus Hollis, who teaches CS 2336 sections 284, 288 and 291 in Fall 2026
    // (semester 13) and has not signed up. Kaia is on the Assessment Committee.
    private static final AccountPrincipal KAIA = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal DARIO = new AccountPrincipal(
            new CurrentUser(2, 34, 1, "Dario Hollowell", "dario.hollowell@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal ARLO = new AccountPrincipal(
            new CurrentUser(3, 26, 1, "Arlo Glimmerton", "arlo.glimmerton@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal HOLLIS = new AccountPrincipal(
            new CurrentUser(null, 9, 1, "Hollis Carrowmore", "hollis.carrowmore@example.edu", List.of(Role.PROFESSOR)),
            null,
            true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private ObserverRepository observers;

    @Autowired
    private ObserverMatchRepository matches;

    @Autowired
    private ObservationRepository observations;

    private void signupDeadline(String date) {
        db.update(withSignupDeadline(db.findById(1, EvaluationCycle.class), LocalDate.parse(date)));
    }

    private List<Observation> observationsOf(int signupId) {
        return observations.findBySignupIdIn(List.of(signupId));
    }

    @Test
    void committeeEndpointsRejectFacultyAndFacultySeeOnlyTheirOwnState() throws Exception {
        mvc.perform(as(DARIO, get("/api/observer-eligibility/ac"))).andExpect(status().isForbidden());
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/9"))).andExpect(status().isForbidden());
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor.professorId").value(34));
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.label").value("Fall 2026"));
        mvc.perform(as(KAIA, post("/api/observer-eligibility/generate"))).andExpect(status().isOk());
    }

    @Test
    void regenerationRequiresAnExhaustedList() throws Exception {
        int signupId = signupOf(db, 31);
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/regenerate")))
                .andExpect(status().isConflict());
    }

    @Test
    void signupSavesTheObservationWindowAndCreatesAnUnreleasedList() throws Exception {
        mvc.perform(as(HOLLIS, get("/api/professors/9/evaluation-signups/13/window")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-11-01"))
                .andExpect(jsonPath("$.latestEnd").value("2026-11-15"));
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284,\"windowEnd\":\"2026-11-10\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(HOLLIS, get("/api/observer-eligibility/faculty/9")))
                .andExpect(jsonPath("$.signup.windowEnd").value("2026-11-10"))
                .andExpect(jsonPath("$.signup.released").value(false));
    }

    // The whole workflow with the demo accounts: Dario signs up, Kaia sends his list, Arlo accepts his request.
    @Test
    void demoWalkthroughFromSignupToAcceptedObservation() throws Exception {
        int signupId = signUpDario();
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(jsonPath("$.signups[?(@.signupId == " + signupId + ")].candidates[*].name")
                        .value(containsInAnyOrder(
                                "Leander Coldwell", "Arlo Glimmerton", "Avery Hazelwood", "Keira Larkspur")));

        signupDeadline("2026-10-20");
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isNoContent());
        int arloMatch = matchOf(db, signupId, 26);
        mvc.perform(as(DARIO, post("/api/professors/34/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + arloMatch + "}"))
                .andExpect(status().isCreated());

        var arloState = mvc.perform(as(ARLO, get("/api/observer-eligibility/faculty/26")))
                .andExpect(jsonPath("$.incoming[0].observeeName").value("Dario Hollowell"))
                .andReturn();
        int requestId = JsonPath.read(arloState.getResponse().getContentAsString(), "$.incoming[0].requestId");
        mvc.perform(as(ARLO, post("/api/observer-eligibility/faculty/26/requests/" + requestId + "/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true,\"observationDate\":\"2026-11-03\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(jsonPath("$.outgoing[0].observerName").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$.outgoing[0].status").value("CONFIRMED"));
    }

    @Test
    void committeeAssignsAnEligibleObserverWithoutARequest() throws Exception {
        int signupId = signUpDario();
        mvc.perform(as(KAIA, get("/api/observer-eligibility/signups/" + signupId + "/eligible")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name")
                        .value(containsInAnyOrder(
                                "Leander Coldwell", "Arlo Glimmerton", "Avery Hazelwood", "Keira Larkspur")));

        // Manual assignment is available only once every generated observer has declined.
        signupDeadline("2026-10-20");
        db.update(withReleasedAt(
                db.findById(signupId, ObserverListState.class), LocalDateTime.parse("2026-10-21T10:00:00")));
        for (var match : matches.findBySignupId(signupId))
            db.insert(new ObserverRequest(
                    null,
                    match.signupId(),
                    match.observerId(),
                    LocalDateTime.parse("2026-10-21T09:00:00"),
                    LocalDateTime.parse("2026-10-23T09:00:00"),
                    LocalDateTime.parse("2026-10-21T10:00:00"),
                    "DECLINED",
                    false));
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/assign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observerId\":" + observerId("arlo.glimmerton@example.edu") + "}"))
                .andExpect(status().isNoContent());

        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(jsonPath("$.matchingCases[?(@.signupId == " + signupId + ")].status")
                        .value(contains("MATCHED")));
        mvc.perform(as(ARLO, get("/api/observer-eligibility/faculty/26")))
                .andExpect(jsonPath("$.incoming[0].observeeName").value("Dario Hollowell"));
        assertThat(only(observationsOf(signupId)).observerId()).isEqualTo(observerId("arlo.glimmerton@example.edu"));
    }

    @Test
    void committeeMembersUpdateCycleDeadlines() throws Exception {
        mvc.perform(
                        as(KAIA, put("/api/observer-eligibility/cycles/1/deadlines"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"signupDeadline\":\"2026-10-31\",\"observationDeadline\":\"2026-11-20\",\"feedbackDeadline\":\"2026-12-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackDeadline").value("2026-12-05"));
    }

    @Test
    void deferralRecordsTheSignedInCommitteeMember() throws Exception {
        int signupId = signUpDario();
        signupDeadline("2026-10-20");
        observers.findByCycleId(1).forEach(o -> db.update(inactive(o)));
        // The actor comes from the session, not from the request body.
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/defer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No matching observers\",\"deferredBy\":34}"))
                .andExpect(status().isNoContent());
        assertThat(db.findById(signupId, EvaluationDeferral.class).deferredBy()).isEqualTo(12);
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(jsonPath("$.matchingCases[?(@.signupId == " + signupId + ")].status")
                        .value(contains("DEFERRED")));
    }

    private int signUpDario() throws Exception {
        mvc.perform(as(DARIO, put("/api/professors/34/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":298}"))
                .andExpect(status().isOk());
        return signupOf(db, 34);
    }

    private int observerId(String email) {
        return observerOf(db, professor(db, email).professorId());
    }

    private MockHttpServletRequestBuilder as(AccountPrincipal professor, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(professor)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
