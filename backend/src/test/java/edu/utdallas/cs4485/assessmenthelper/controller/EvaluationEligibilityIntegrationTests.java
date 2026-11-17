package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Tests the real schema, seed records, JDBC repositories, service, and HTTP endpoints together. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EvaluationEligibilityIntegrationTests.FixedTime.class)
@Transactional
@WithMockUser(roles = "AC")
class EvaluationEligibilityIntegrationTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock reviewClock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    // From data.sql: Hollis (professor 9) is due this term and has not signed up. Leander (professor 13) missed his
    // first evaluation in Spring 2026 and has a scheduled observation in this cycle (signup 33).
    private static final int HOLLIS = 9;
    private static final int LEANDER = 13;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private EvaluationNotificationRepository notifications;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private SemesterRepository semesters;

    @Autowired
    private EvaluationEligibilityService service;

    @Test
    void countsAre13Due15OverdueAnd73UpcomingWhenSeedDataIsLoaded() throws Exception {
        mvc.perform(get("/api/due-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(13))
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"));
        mvc.perform(get("/api/overdue-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(15));
        mvc.perform(get("/api/evaluation-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(101));
        assertThat(service.findAll().faculty().stream().filter(p -> p.status().equals("Upcoming")))
                .hasSize(73);
        assertThat(service.findDue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .contains(HOLLIS);
        assertThat(service.findOverdue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .contains(LEANDER);
    }

    @Test
    void reminderSavesEditedTextWithoutChangingCountsWhenSent() throws Exception {
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/" + HOLLIS + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"hollis.carrowmore@example.edu","subject":"Edited reminder","body":"Dear Professor Carrowmore, please sign up."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Edited reminder"));
        mvc.perform(withCsrfToken(post("/api/overdue-evaluations/1/professors/" + LEANDER + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"leander.coldwell@example.edu","subject":"Overdue reminder","body":"Dear Professor Coldwell, please sign up."}
                                """))
                .andExpect(status().isOk());
        assertThat(notifications.findAll())
                .filteredOn(
                        n -> n.professorId() == HOLLIS && n.body().equals("Dear Professor Carrowmore, please sign up."))
                .hasSize(1);
        assertThat(service.findDue().count()).isEqualTo(13);
        assertThat(service.findOverdue().count()).isEqualTo(15);
    }

    @Test
    void reminderIsRejectedWhenRecipientIsWrong() throws Exception {
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/" + HOLLIS + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"wrong@example.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(notifications.count()).isZero();
    }

    @Test
    @WithMockUser
    void evaluationListsReturn403WhenCallerIsNotOnTheCommittee() throws Exception {
        mvc.perform(get("/api/due-evaluations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/overdue-evaluations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/evaluation-eligibility")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder withCsrfToken(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
