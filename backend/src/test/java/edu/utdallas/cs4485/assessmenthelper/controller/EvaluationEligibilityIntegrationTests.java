package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.completed;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationEligibilityService;
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
    private static final int LEANDER_SIGNUP = 33;

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
    void seededProfessorsProduceThirteenDueFifteenOverdueAndSeventyThreeUpcoming() throws Exception {
        assertThat(professors.count()).isEqualTo(161);
        var due = service.findDue();
        var overdue = service.findOverdue();
        assertThat(due.count()).isEqualTo(13);
        assertThat(overdue.count()).isEqualTo(15);
        assertThat(overdue.faculty().stream()
                        .filter(p -> p.professorId() == LEANDER)
                        .findFirst()
                        .orElseThrow()
                        .nextDueSemester())
                .isEqualTo("Spring 2026");
        var all = service.findAll();
        assertThat(all.count()).isEqualTo(101);
        assertThat(all.faculty())
                .allSatisfy(
                        professor -> assertThat(professor.nextDueSemester()).matches("(Spring|Summer|Fall) \\d{4}"));
        assertThat(all.faculty().stream()
                        .filter(p -> p.lastName().equals("Juniper"))
                        .findFirst()
                        .orElseThrow()
                        .nextDueSemester())
                .isEqualTo("Spring 2028");
        assertThat(all.faculty().stream().filter(p -> p.lastEvaluationCycle().equals("Never evaluated")))
                .extracting(EvaluationEligibilityService.Faculty::lastName)
                .containsExactlyInAnyOrder(
                        "Bramblecote",
                        "Brambleton",
                        "Cedarvale",
                        "Calderwood",
                        "Coldwell",
                        "Dappleton",
                        "Duskwood",
                        "Eastbrook",
                        "Elmhollow",
                        "Harrowgate",
                        "Heronsgate",
                        "Hollowell",
                        "Inglemoor",
                        "Ivorydale",
                        "Juniper",
                        "Lavenham",
                        "Leafwood",
                        "Millbrook",
                        "Netherby",
                        "Orchardleigh",
                        "Pinegrove",
                        "Quarrington",
                        "Ravenhill",
                        "Reedmoor",
                        "Rookwood",
                        "Rushcombe",
                        "Tamberlyn",
                        "Thornbury",
                        "Thornfield",
                        "Timberlake",
                        "Valemont",
                        "Whitlow",
                        "Winterbourne",
                        "Wrenfield",
                        "Yarrowby",
                        "Yewbank");
        assertThat(all.faculty().stream().filter(p -> p.status().equals("Due")))
                .containsExactlyElementsOf(due.faculty());
        assertThat(all.faculty().stream().filter(p -> p.status().equals("Overdue")))
                .containsExactlyElementsOf(overdue.faculty());
        assertThat(all.faculty().stream().filter(p -> p.status().equals("Upcoming")))
                .hasSize(73);
        mvc.perform(get("/api/evaluation-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(101))
                .andExpect(jsonPath("$.faculty.length()").value(101));
        assertThat(overdue.faculty().stream()
                        .filter(professor -> !professor.lastEvaluationCycle().equals("Never evaluated")))
                .hasSize(6);
        assertThat(due.faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContainAnyElementsOf(overdue.faculty().stream()
                        .map(EvaluationEligibilityService.Faculty::professorId)
                        .toList());
        assertThat(due.faculty())
                .extracting(EvaluationEligibilityService.Faculty::lastName)
                .contains("Carrowmore");
        assertThat(overdue.faculty())
                .extracting(EvaluationEligibilityService.Faculty::lastName)
                .contains("Coldwell");
        mvc.perform(get("/api/due-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(13))
                .andExpect(jsonPath("$.faculty.length()").value(13))
                .andExpect(jsonPath("$.cycleLabel").value("Fall 2026"));
        mvc.perform(get("/api/overdue-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(15));
    }

    @Test
    void remindersAndFollowUpsSaveEditedContentWithoutChangingCounts() throws Exception {
        String hollisMessage = """
                {"recipient":"hollis.carrowmore@example.edu","subject":"Edited reminder","body":"Dear Professor Carrowmore, please sign up."}
                """;
        for (int i = 0; i < 2; i++) {
            mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/" + HOLLIS + "/notifications"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(hollisMessage))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notificationId").isNumber())
                    .andExpect(jsonPath("$.recipient").value("hollis.carrowmore@example.edu"))
                    .andExpect(jsonPath("$.subject").value("Edited reminder"))
                    .andExpect(jsonPath("$.sentAt").value("2026-10-21T10:00:00"));
        }
        mvc.perform(withCsrfToken(post("/api/overdue-evaluations/1/professors/" + LEANDER + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"leander.coldwell@example.edu","subject":"Overdue reminder","body":"Dear Professor Coldwell, please sign up."}
                                """))
                .andExpect(status().isOk());
        assertThat(notifications.count()).isEqualTo(3);
        assertThat(notifications.findAll())
                .filteredOn(
                        n -> n.professorId() == HOLLIS && n.body().equals("Dear Professor Carrowmore, please sign up."))
                .hasSize(2);
        assertThat(service.findDue().count()).isEqualTo(13);
        assertThat(service.findOverdue().count()).isEqualTo(15);
        assertThat(service.findDue().faculty().stream()
                        .filter(p -> p.professorId() == HOLLIS)
                        .findFirst()
                        .orElseThrow()
                        .lastNotifiedAt())
                .isNotNull();
        assertThat(service.findOverdue().faculty().stream()
                        .filter(p -> p.professorId() == LEANDER)
                        .findFirst()
                        .orElseThrow()
                        .lastNotifiedAt())
                .isNotNull();
    }

    @Test
    void invalidOrStaleNotificationsNeverWriteToDatabase() throws Exception {
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/" + HOLLIS + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"wrong@example.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(withCsrfToken(post("/api/due-evaluations/1/professors/" + LEANDER + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"leander.coldwell@example.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(withCsrfToken(post("/api/due-evaluations/99/professors/" + HOLLIS + "/notifications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"hollis.carrowmore@example.edu","subject":"Reminder","body":"Message"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The evaluation cycle changed. Refresh the evaluation list."));
        assertThat(notifications.count()).isZero();
    }

    @Test
    void completingAnOverdueEvaluationClearsItsOldObligation() throws Exception {
        observees.findByProfessorId(LEANDER).stream()
                .filter(o -> o.cycleId() == 1)
                .forEach(o -> observees.save(new Observee(o.observeeId(), o.professorId(), o.cycleId(), "COMPLETED")));
        observations
                .findBySignupIdIn(List.of(LEANDER_SIGNUP))
                .forEach(o -> observations.save(completed(o, LocalDateTime.of(2026, 10, 20, 10, 0))));
        mvc.perform(get("/api/overdue-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(14));
        assertThat(service.findDue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContain(LEANDER);
        assertThat(service.findOverdue().faculty())
                .extracting(EvaluationEligibilityService.Faculty::professorId)
                .doesNotContain(LEANDER);
    }

    @Test
    void inactiveFacultyAreExcludedAndMissingCycleConfigurationIsReported() throws Exception {
        professors.findAllById(List.of(HOLLIS, LEANDER)).forEach(p -> professors.save(inactive(p)));
        assertThat(service.findDue().count()).isEqualTo(12);
        assertThat(service.findOverdue().count()).isEqualTo(14);
        semesters.findAll().stream()
                .filter(semester -> semester.term().equals("Fall") && semester.academicYear() == 2026)
                .forEach(semester -> semesters.save(new Semester(
                        semester.semesterId(),
                        semester.term(),
                        semester.academicYear(),
                        LocalDate.of(2028, 8, 21),
                        LocalDate.of(2028, 12, 15))));
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
        mvc.perform(get("/api/evaluation-eligibility")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder withCsrfToken(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
}
