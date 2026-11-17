package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationAuditLogRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * The observee's sign-off and the committee's forward over HTTP. The rules behind them (who may sign, locking, the audit
 * log) are covered in SignOffAndLockingAcceptanceTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationLockingIntegrationTests.FixedTime.class)
@Transactional
class ObservationLockingIntegrationTests {

    private static final String NOW = "2026-11-04T10:15:42";

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock lockingClock() {
            return Clock.fixed(Instant.parse("2026-11-04T16:15:42Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcAggregateTemplate db;

    @Autowired
    private ObservationRepository observations;

    @Autowired
    private ObservationCriterionRepository criteria;

    @Autowired
    private ObservationAuditLogRepository auditLog;

    // From data.sql: Keira observes Leander's CS 3345.001 and has not started. Kaia is on the Assessment Committee;
    // Dario has no part in this observation.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int observation;
    private List<Integer> questions;

    @BeforeEach
    void scheduledObservation() {
        keira = principal(db, "keira.larkspur@example.edu", 1, Role.PROFESSOR);
        leander = principal(db, "leander.coldwell@example.edu", 1, Role.PROFESSOR);
        kaia = principal(db, "kaia.clearbrook@example.edu", 1, Role.AC, Role.PROFESSOR);
        dario = principal(db, "dario.hollowell@example.edu", 1, Role.PROFESSOR);
        observation = observationOf(db, "leander.coldwell@example.edu", "Fall", 2026);
        questions = criteria
                .findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(current().templateId())
                .stream()
                .filter(ObservationCriterion::selected)
                .map(ObservationCriterion::criterionId)
                .toList();
    }

    @Test
    void theObserveeSignsOffWithCommentsAndThatCompletesAndLocksTheRecord() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira).andExpect(status().isOk());

        sign(leander, "The scores are fair, thank you.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.canSign").value(false))
                .andExpect(jsonPath("$.observeeComments").value("The scores are fair, thank you."))
                .andExpect(jsonPath("$.observeeSignedAt").value(NOW));
        sign(leander, "Again.").andExpect(status().isConflict());
    }

    @Test
    void onlyACommitteeMemberCanForwardACompletedRecordToAProfessor() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira).andExpect(status().isOk());
        sign(leander, null).andExpect(status().isOk());

        forward(leander, "{\"recipientProfessorId\":" + id(dario) + "}").andExpect(status().isForbidden());
        forward(kaia, "{}").andExpect(status().isBadRequest());
        forward(kaia, "{\"recipientProfessorId\":" + id(dario) + "}").andExpect(status().isNoContent());

        assertThat(auditLog.findByObservationIdOrderByLogIdAsc(observation))
                .filteredOn(e -> e.eventType().equals("FORWARDED"))
                .hasSize(1);
        // A committee member of another school may forward it too.
        forward(otherSchoolCommittee(), "{\"recipientProfessorId\":" + id(dario) + "}")
                .andExpect(status().isNoContent());
    }

    private ResultActions start(AccountPrincipal principal) throws Exception {
        return write(principal, post("/api/observations/" + observation + "/start"));
    }

    // Submits a complete form: all five ratings and the required strengths.
    private ResultActions submit(AccountPrincipal principal) throws Exception {
        List<Answer> answers = List.of(
                new Answer(questions.get(0), 4, null),
                new Answer(questions.get(1), 5, null),
                new Answer(questions.get(2), 4, null),
                new Answer(questions.get(3), 3, null),
                new Answer(questions.get(4), 4, null),
                new Answer(questions.get(5), null, "Clear explanations and an easy rapport with students."));
        return write(
                principal,
                post("/api/observations/" + observation + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ObservationController.ResponsesBody(answers))));
    }

    private ResultActions sign(AccountPrincipal principal, String comments) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/observee-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ObservationController.SignOffBody(comments))));
    }

    private ResultActions forward(AccountPrincipal principal, String body) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/forward")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    // Signs the request in and adds a CSRF token the way the frontend gets one (see
    // FacultyObserverRequestIntegrationTests for why the csrf() helper is not used).
    private ResultActions write(AccountPrincipal principal, MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(request.with(user(principal)).cookie(token).header("X-XSRF-TOKEN", token.getValue()));
    }

    private Observation current() {
        return observations.findById(observation).orElseThrow();
    }

    // Kaia as a committee member of another school.
    private AccountPrincipal otherSchoolCommittee() {
        return new AccountPrincipal(
                new CurrentUser(null, id(kaia), 2, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC)),
                null,
                true);
    }

    private static Integer id(AccountPrincipal principal) {
        return principal.user().professorId();
    }
}
