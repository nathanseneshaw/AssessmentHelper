package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationAuditLog;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationAuditLogRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.ViewerRole;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservationAuditLogIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObservationService observations;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private ObservationAuditLogRepository auditLog;

    @Autowired
    private JdbcAggregateTemplate db;

    // From data.sql: Keira observes Leander's CS 3345.001 this fall, Kaia is on the Assessment Committee of the same
    // school, and Dario has no part in the observation.
    private AccountPrincipal keira;
    private AccountPrincipal leander;
    private AccountPrincipal kaia;
    private AccountPrincipal dario;
    private int observation;

    @BeforeEach
    void completedAndForwardedObservation() {
        keira = principal("keira.larkspur@example.edu", Role.PROFESSOR);
        leander = principal("leander.coldwell@example.edu", Role.PROFESSOR);
        kaia = principal("kaia.clearbrook@example.edu", Role.AC, Role.PROFESSOR);
        dario = principal("dario.hollowell@example.edu", Role.PROFESSOR);
        observation = observations.professorObservations(id(leander)).stream()
                .filter(o ->
                        o.role() == ViewerRole.OBSERVEE && o.semesterLabel().equals("Fall 2026"))
                .findFirst()
                .orElseThrow()
                .observationId();

        observations.start(observation, keira);
        List<Criterion> questions =
                observations.detail(observation, keira).template().criteria();
        observations.submit(
                observation,
                questions.stream()
                        .map(ObservationAuditLogIntegrationTests::answer)
                        .toList(),
                keira);
        observations.observeeSign(observation, null, leander);
        observations.forward(observation, id(dario), kaia);
    }

    @Test
    void committeeMemberReadsTheLogOldestFirst() throws Exception {
        read(kaia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].event")
                        .value(contains("STARTED", "SUBMITTED", "OBSERVEE_SIGNED", "LOCKED", "FORWARDED")))
                .andExpect(jsonPath("$[0].actorName").value("Keira Larkspur"))
                .andExpect(jsonPath("$[0].recipientName").value(nullValue()))
                .andExpect(jsonPath("$[4].actorName").value("Kaia Clearbrook"))
                .andExpect(jsonPath("$[4].recipientName").value("Dario Hollowell"));
    }

    @Test
    void onlyTheObserveesSchoolCommitteeMayReadTheLog() throws Exception {
        for (AccountPrincipal other : List.of(keira, leander, dario))
            read(other).andExpect(status().isForbidden());
        AccountPrincipal otherSchool = new AccountPrincipal(
                new CurrentUser(null, id(kaia), 2, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC)),
                null,
                true);
        read(otherSchool).andExpect(status().isForbidden());
        mvc.perform(get("/api/observations/999999/audit-log").with(user(kaia))).andExpect(status().isNotFound());
    }

    @Test
    void theDatabaseRejectsChangesAndDeletes() {
        List<ObservationAuditLog> before = auditLog.findByObservationIdOrderByLogIdAsc(observation);
        ObservationAuditLog first = before.get(0);

        assertThatThrownBy(() -> db.update(new ObservationAuditLog(
                        first.logId(),
                        first.observationId(),
                        "VIEWED",
                        first.actorProfessorId(),
                        null,
                        first.occurredAt())))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.delete(first)).rootCause().hasMessageContaining("append-only");
        assertThat(auditLog.findByObservationIdOrderByLogIdAsc(observation)).isEqualTo(before);
    }

    private static Answer answer(Criterion question) {
        return "RATING".equals(question.questionType())
                ? new Answer(question.criterionId(), Objects.requireNonNullElse(question.ratingMax(), 5), null)
                : new Answer(question.criterionId(), null, "Clear explanations.");
    }

    private ResultActions read(AccountPrincipal principal) throws Exception {
        return mvc.perform(
                get("/api/observations/" + observation + "/audit-log").with(user(principal)));
    }

    private static Integer id(AccountPrincipal principal) {
        return principal.user().professorId();
    }

    private AccountPrincipal principal(String email, Role... roles) {
        Professor professor = professors.findAll().stream()
                .filter(p -> email.equals(p.email()))
                .findFirst()
                .orElseThrow();
        return new AccountPrincipal(
                new CurrentUser(
                        null,
                        professor.professorId(),
                        1,
                        professor.firstName() + " " + professor.lastName(),
                        email,
                        List.of(roles)),
                null,
                true);
    }
}
