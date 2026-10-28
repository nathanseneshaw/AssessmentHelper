package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private JdbcTemplate db;

    @Test
    void committeeEndpointsRejectFaculty() throws Exception {
        mvc.perform(as(DARIO, get("/api/observer-eligibility/ac"))).andExpect(status().isForbidden());
        mvc.perform(as(DARIO, post("/api/observer-eligibility/generate"))).andExpect(status().isForbidden());
        mvc.perform(as(DARIO, post("/api/observer-eligibility/signups/30/release")))
                .andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.label").value("Fall 2026"))
                .andExpect(jsonPath("$.signups.length()").value(6));
        mvc.perform(as(KAIA, post("/api/observer-eligibility/generate"))).andExpect(status().isOk());
    }

    @Test
    void facultySeeOnlyTheirOwnStateAndAnswerOnlyTheirOwnRequests() throws Exception {
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor.professorId").value(34));
        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/9"))).andExpect(status().isForbidden());
        mvc.perform(as(KAIA, get("/api/observer-eligibility/faculty/9"))).andExpect(status().isOk());

        mvc.perform(as(DARIO, post("/api/observer-eligibility/faculty/41/requests/1/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(DARIO, post("/api/observer-eligibility/faculty/34/requests/99999/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(DARIO, post("/api/observer-eligibility/faculty/34/requests/1/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupSavesTheObservationWindowAndCreatesAnUnreleasedList() throws Exception {
        mvc.perform(as(HOLLIS, get("/api/professors/9/evaluation-signups/13/window")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-11-01"))
                .andExpect(jsonPath("$.latestEnd").value("2026-11-15"))
                .andExpect(jsonPath("$.end").isEmpty())
                .andExpect(jsonPath("$.signupOpen").value(true));
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284,\"windowEnd\":\"2026-11-20\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(HOLLIS, put("/api/professors/9/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":284,\"windowEnd\":\"2026-11-10\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(HOLLIS, get("/api/professors/9/evaluation-signups/13/window")))
                .andExpect(jsonPath("$.end").value("2026-11-10"));
        mvc.perform(as(HOLLIS, get("/api/observer-eligibility/faculty/9")))
                .andExpect(jsonPath("$.signup.windowEnd").value("2026-11-10"))
                .andExpect(jsonPath("$.signup.released").value(false));
        mvc.perform(as(DARIO, get("/api/professors/9/evaluation-signups/13/window")))
                .andExpect(status().isForbidden());
    }

    // The whole workflow with the demo accounts: Dario signs up, Kaia sends his list, Arlo accepts his request.
    @Test
    void demoWalkthroughFromSignupToAcceptedObservation() throws Exception {
        mvc.perform(as(DARIO, put("/api/professors/34/evaluation-signups/13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":298}"))
                .andExpect(status().isOk());
        int signupId = db.queryForObject("""
                SELECT s.signup_id FROM evaluation_signup s JOIN observee e ON e.observee_id = s.observee_id
                WHERE e.professor_id = 34 AND e.cycle_id = 1
                """, Integer.class);
        mvc.perform(as(KAIA, get("/api/observer-eligibility/ac")))
                .andExpect(jsonPath("$.signups[?(@.signupId == " + signupId + ")].candidates[*].name")
                        .value(containsInAnyOrder(
                                "Leander Coldwell", "Arlo Glimmerton", "Avery Hazelwood", "Keira Larkspur")));
        mvc.perform(as(DARIO, get("/api/professors/34/observer-requests")))
                .andExpect(jsonPath("$[0].candidates.length()").value(0))
                .andExpect(
                        jsonPath("$[0].unavailableReason").value("Your observer list is awaiting committee review."));

        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isConflict());
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        mvc.perform(as(KAIA, post("/api/observer-eligibility/signups/" + signupId + "/release")))
                .andExpect(status().isNoContent());
        int arloMatch = db.queryForObject("""
                SELECT m.match_id FROM observer_match m JOIN observer o ON o.observer_id = m.observer_id
                WHERE m.signup_id = ? AND o.professor_id = 26
                """, Integer.class, signupId);
        mvc.perform(as(DARIO, post("/api/professors/34/observer-requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupId\":" + signupId + ",\"matchId\":" + arloMatch + "}"))
                .andExpect(status().isCreated());

        var arloState = mvc.perform(as(ARLO, get("/api/observer-eligibility/faculty/26")))
                .andExpect(jsonPath("$.incoming.length()").value(1))
                .andExpect(jsonPath("$.incoming[0].observeeName").value("Dario Hollowell"))
                .andReturn();
        int requestId = JsonPath.read(arloState.getResponse().getContentAsString(), "$.incoming[0].requestId");
        mvc.perform(as(ARLO, post("/api/observer-eligibility/faculty/26/requests/" + requestId + "/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isNoContent());

        mvc.perform(as(DARIO, get("/api/observer-eligibility/faculty/34")))
                .andExpect(jsonPath("$.outgoing[0].observerName").value("Arlo Glimmerton"))
                .andExpect(jsonPath("$.outgoing[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Arlo Glimmerton accepted your observation request for CS 3345.005."));
    }

    private MockHttpServletRequestBuilder as(AccountPrincipal professor, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(professor)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
