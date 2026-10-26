package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    // Professors from test-data.sql. Alice is on the Assessment Committee.
    private static final AccountPrincipal ALICE = new AccountPrincipal(
            new CurrentUser(1, 1, 1, "Alice Alice", "alice@utdallas.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);
    private static final AccountPrincipal BOB = new AccountPrincipal(
            new CurrentUser(2, 2, 1, "Bob Bob", "bob@utdallas.edu", List.of(Role.PROFESSOR)), null, true);
    private static final AccountPrincipal CAROL = new AccountPrincipal(
            new CurrentUser(3, 3, 1, "Carol Carol", "carol@utdallas.edu", List.of(Role.PROFESSOR)), null, true);

    @Autowired
    private MockMvc mvc;

    @Test
    void committeeEndpointsRejectFaculty() throws Exception {
        mvc.perform(as(BOB, get("/api/observer-eligibility/ac"))).andExpect(status().isForbidden());
        mvc.perform(as(BOB, post("/api/observer-eligibility/generate"))).andExpect(status().isForbidden());
        mvc.perform(as(BOB, post("/api/observer-eligibility/signups/1/release")))
                .andExpect(status().isForbidden());
        mvc.perform(as(ALICE, get("/api/observer-eligibility/ac")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.label").value("Fall 2026"));
        mvc.perform(as(ALICE, post("/api/observer-eligibility/generate"))).andExpect(status().isOk());
    }

    @Test
    void facultySeeOnlyTheirOwnStateAndAnswerOnlyTheirOwnRequests() throws Exception {
        mvc.perform(as(BOB, get("/api/observer-eligibility/faculty/2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor.professorId").value(2));
        mvc.perform(as(BOB, get("/api/observer-eligibility/faculty/3"))).andExpect(status().isForbidden());
        mvc.perform(as(ALICE, get("/api/observer-eligibility/faculty/3"))).andExpect(status().isOk());

        mvc.perform(as(BOB, post("/api/observer-eligibility/faculty/3/requests/1/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(BOB, post("/api/observer-eligibility/faculty/2/requests/99999/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(BOB, post("/api/observer-eligibility/faculty/2/requests/1/respond"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupSavesTheObservationWindowAndCreatesAnUnreleasedList() throws Exception {
        mvc.perform(as(CAROL, get("/api/professors/3/evaluation-signups/1/window")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-11-01"))
                .andExpect(jsonPath("$.latestEnd").value("2026-11-15"))
                .andExpect(jsonPath("$.end").isEmpty())
                .andExpect(jsonPath("$.signupOpen").value(true));
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9,\"windowEnd\":\"2026-11-20\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(CAROL, put("/api/professors/3/evaluation-signups/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":9,\"windowEnd\":\"2026-11-10\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(CAROL, get("/api/professors/3/evaluation-signups/1/window")))
                .andExpect(jsonPath("$.end").value("2026-11-10"));
        mvc.perform(as(CAROL, get("/api/observer-eligibility/faculty/3")))
                .andExpect(jsonPath("$.signup.windowEnd").value("2026-11-10"))
                .andExpect(jsonPath("$.signup.released").value(false));
        mvc.perform(as(BOB, get("/api/professors/3/evaluation-signups/1/window")))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(AccountPrincipal professor, MockHttpServletRequestBuilder request)
            throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(user(professor)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
