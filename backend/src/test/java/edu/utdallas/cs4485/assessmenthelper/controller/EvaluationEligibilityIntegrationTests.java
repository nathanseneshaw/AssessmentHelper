package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private EvaluationEligibilityService service;

    @Test
    void seededProfessorsProduceTenDueTenOverdueAndFiveUpcoming() throws Exception {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM professor", Integer.class))
                .isEqualTo(25);
        var due = service.findDue();
        var overdue = service.findOverdue();
        assertThat(due.count()).isEqualTo(10);
        assertThat(overdue.count()).isEqualTo(10);
        assertThat(overdue.faculty().stream()
                        .filter(professor -> !professor.lastEvaluationCycle().equals("Never evaluated")))
                .hasSize(9);
        assertThat(due.faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContainAnyElementsOf(overdue.faculty().stream()
                        .map(EvaluationEligibilityService.Faculty::professorId)
                        .toList());
        assertThat(due.faculty())
                .extracting(EvaluationEligibilityService.Faculty::lastName)
                .contains("Carol");
        assertThat(overdue.faculty())
                .extracting(EvaluationEligibilityService.Faculty::lastName)
                .contains("Bob");
        mvc.perform(get("/api/due-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.faculty.length()").value(10))
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"));
        mvc.perform(get("/api/overdue-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10));
    }

    @Test
    void remindersAndFollowUpsSaveEditedContentWithoutChangingCounts() throws Exception {
        String carolMessage = """
                {"recipient":"carol@utdallas.edu","subject":"Edited reminder","body":"Dear Professor Carol, please sign up."}
                """;
        for (int i = 0; i < 2; i++) {
            mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/3/notifications"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(carolMessage))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notificationId").isNumber())
                    .andExpect(jsonPath("$.recipient").value("carol@utdallas.edu"))
                    .andExpect(jsonPath("$.subject").value("Edited reminder"))
                    .andExpect(jsonPath("$.sentAt").value("2026-10-21T10:00:00"));
        }
        mvc.perform(withCsrfToken(post("/api/overdue-evaluations/1/professors/2/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"bob@utdallas.edu","subject":"Overdue reminder","body":"Dear Professor Bob, please sign up."}
                                """))
                .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM evaluation_notification", Integer.class))
                .isEqualTo(3);
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM evaluation_notification WHERE professor_id = 3 AND body = ?",
                        Integer.class,
                        "Dear Professor Carol, please sign up."))
                .isEqualTo(2);
        assertThat(service.findDue().count()).isEqualTo(10);
        assertThat(service.findOverdue().count()).isEqualTo(10);
        assertThat(service.findDue().faculty().stream()
                        .filter(p -> p.professorId() == 3)
                        .findFirst()
                        .orElseThrow()
                        .lastNotifiedAt())
                .isNotNull();
        assertThat(service.findOverdue().faculty().stream()
                        .filter(p -> p.professorId() == 2)
                        .findFirst()
                        .orElseThrow()
                        .lastNotifiedAt())
                .isNotNull();
    }

    @Test
    void invalidOrStaleNotificationsNeverWriteToDatabase() throws Exception {
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/3/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"wrong@utdallas.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/2/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"bob@utdallas.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(withCsrfToken(post("/api/due-evaluations/99/professors/3/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"carol@utdallas.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The evaluation cycle changed. Refresh the evaluation list."));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM evaluation_notification", Integer.class))
                .isZero();
    }

    @Test
    void completingAnOverdueEvaluationClearsItsOldObligation() throws Exception {
        db.update("UPDATE observee SET status = 'COMPLETED' WHERE professor_id = 2 AND cycle_id = 1");
        db.update(
                "UPDATE observation SET status = 'COMPLETED', completed_at = TIMESTAMP '2026-10-20 10:00:00' WHERE signup_id = 1");
        mvc.perform(get("/api/overdue-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(9));
        assertThat(service.findDue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContain(2);
        assertThat(service.findOverdue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContain(2);
    }

    @Test
    void inactiveFacultyAreExcludedAndMissingCycleConfigurationIsReported() throws Exception {
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id IN (2, 3)");
        assertThat(service.findDue().count()).isEqualTo(9);
        assertThat(service.findOverdue().count()).isEqualTo(9);
        db.update(
                "UPDATE semester SET start_date = DATE '2027-08-24', end_date = DATE '2027-12-11' WHERE semester_id = 1");
        mvc.perform(get("/api/due-evaluations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("No unique semester contains today's date. Configure semester dates first."));
    }

    @Test
    @WithMockUser
    void onlyCommitteeMembersCanSeeTheLists() throws Exception {
        mvc.perform(get("/api/due-evaluations")).andExpect(status().isForbidden());
        mvc.perform(get("/api/overdue-evaluations")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder withCsrfToken(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
