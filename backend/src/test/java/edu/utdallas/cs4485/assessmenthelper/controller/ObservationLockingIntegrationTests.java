package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationAuditLog;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverNotification;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationAuditLogRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * The observer submits, the observee signs off, and that completes and locks the record. Everything that happens to a
 * record is written to the audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationLockingIntegrationTests.FixedTime.class)
@Transactional
class ObservationLockingIntegrationTests {

    private static final String NOW = "2026-11-04T10:15:42";
    private static final String SUBMITTED = "This observation has already been submitted.";

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
    private EvaluationSignupRepository signups;

    @Autowired
    private ObserveeRepository observees;

    @Autowired
    private ObservationCriterionRepository criteria;

    @Autowired
    private ObservationAuditLogRepository auditLog;

    @Autowired
    private ObserverNotificationRepository notifications;

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
    void submittingFreezesTheObserversAnswersButTheRecordIsNotLockedYet() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canSign").value(false));

        assertThat(events()).extracting(ObservationAuditLog::eventType).containsExactly("STARTED", "SUBMITTED");
        assertThat(events()).allSatisfy(e -> {
            assertThat(e.actorProfessorId()).isEqualTo(id(keira));
            assertThat(e.recipientProfessorId()).isNull();
        });
        assertThat(eventsAtNow()).isEqualTo(2);
        // The observer's answers cannot change, and a submitted record is not logged when it is read.
        save(keira, List.of(answer(1, 1, "Changed it.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SUBMITTED));
        read(leander).andExpect(status().isOk()).andExpect(jsonPath("$.canSign").value(true));
        assertThat(events()).hasSize(2);
    }

    @Test
    void theObserveeSignsOffWithCommentsAndThatCompletesAndLocksTheRecord() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());

        sign(leander, "  The scores are fair, thank you.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.viewerRole").value("OBSERVEE"))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.canSign").value(false))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.observeeComments").value("The scores are fair, thank you."))
                .andExpect(jsonPath("$.observeeSignedAt").value(NOW))
                .andExpect(jsonPath("$.responses.length()").value(6));
        assertThat(observeeStatus()).isEqualTo("COMPLETED");

        Observation row = current();
        assertThat(row.observerSigned()).isTrue();
        assertThat(row.observeeSigned()).isTrue();
        assertThat(row.observeeSignedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(row.completedAt()).isEqualTo(LocalDateTime.parse(NOW));

        assertThat(events())
                .extracting(ObservationAuditLog::eventType)
                .containsExactly("STARTED", "SUBMITTED", "OBSERVEE_COMMENTED", "OBSERVEE_SIGNED", "LOCKED");
        assertThat(events().subList(2, 5))
                .allSatisfy(e -> assertThat(e.actorProfessorId()).isEqualTo(id(leander)));
        assertThat(eventsAtNow()).isEqualTo(5);
        // The observer is told the observation is complete.
        assertThat(notificationsTo(keira))
                .containsExactly("Leander Coldwell signed off on the observation of CS 3345.001. It is complete now.");
    }

    @Test
    void theObserveeCanSignOffWithoutComments() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());

        sign(leander, "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.observeeComments").value(nullValue()));
        assertThat(events())
                .extracting(ObservationAuditLog::eventType)
                .containsExactly("STARTED", "SUBMITTED", "OBSERVEE_SIGNED", "LOCKED");
    }

    @Test
    void onlyTheObserveeCanSignOffOnlyOnceAndOnlyAfterTheObserverSubmitted() throws Exception {
        start(keira).andExpect(status().isOk());
        // Not submitted yet: the observee has nothing to sign, and nobody else may try.
        sign(leander, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The observer has not submitted this observation yet."));
        submit(keira, complete()).andExpect(status().isOk());

        for (AccountPrincipal other : List.of(keira, kaia, dario))
            sign(other, "Mine.").andExpect(status().isForbidden());
        sign(leander, "x".repeat(4001)).andExpect(status().isBadRequest());
        assertThat(statusOf()).isEqualTo("SUBMITTED");

        sign(leander, "Fine.").andExpect(status().isOk());
        sign(leander, "Again.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This observation has already been signed off."));
        assertThat(current().observeeComments()).isEqualTo("Fine.");
        assertThat(events()).extracting(ObservationAuditLog::eventType).containsOnlyOnce("OBSERVEE_SIGNED", "LOCKED");
    }

    @Test
    void aDraftIsNotLockedAndViewingItIsNotLogged() throws Exception {
        start(keira).andExpect(status().isOk());
        read(keira).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));
        read(leander).andExpect(status().isForbidden());
        assertThat(events()).extracting(ObservationAuditLog::eventType).containsExactly("STARTED");
    }

    @Test
    void startingAndSavingAreLogged() throws Exception {
        start(keira).andExpect(status().isOk());
        save(keira, List.of()).andExpect(status().isOk());
        assertThat(events()).extracting(ObservationAuditLog::eventType).containsExactly("STARTED", "EDITED");
        read(keira)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedAt").value(nullValue()));
    }

    @Test
    void aCompletedRecordRejectsEveryChangeAndLogsNothingMore() throws Exception {
        complete(null);
        List<String> before =
                events().stream().map(ObservationAuditLog::eventType).toList();
        assertThat(before).containsExactly("STARTED", "SUBMITTED", "OBSERVEE_SIGNED", "LOCKED");

        start(keira)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SUBMITTED));
        save(keira, List.of(answer(1, 1, "Changed it.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SUBMITTED));
        submit(keira, complete())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SUBMITTED));
        sign(leander, "Changed my mind.").andExpect(status().isConflict());
        for (AccountPrincipal other : List.of(leander, kaia, dario)) {
            save(other, List.of(answer(1, 1, null))).andExpect(status().isForbidden());
            submit(other, complete()).andExpect(status().isForbidden());
        }
        assertThat(events().stream().map(ObservationAuditLog::eventType).toList())
                .isEqualTo(before);
    }

    @Test
    void onlyViewsOfACompletedRecordAreLoggedAndRefusedViewsAreNot() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());
        read(leander).andExpect(status().isOk());
        read(kaia).andExpect(status().isForbidden());
        assertThat(events()).extracting(ObservationAuditLog::eventType).doesNotContain("VIEWED");

        sign(leander, null).andExpect(status().isOk());
        read(keira).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
        read(leander).andExpect(status().isOk());
        read(kaia).andExpect(status().isOk());
        read(dario).andExpect(status().isForbidden());

        var views =
                events().stream().filter(e -> "VIEWED".equals(e.eventType())).toList();
        assertThat(views)
                .extracting(ObservationAuditLog::actorProfessorId)
                .containsExactly(id(keira), id(leander), id(kaia));
        assertThat(views).extracting(ObservationAuditLog::recipientProfessorId).containsOnlyNulls();
    }

    @Test
    void committeeMembersReadACompletedRecordButNeverChangeIt() throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());
        read(kaia).andExpect(status().isForbidden());
        sign(leander, "Thanks.").andExpect(status().isOk());

        read(kaia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("COMMITTEE"))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canSign").value(false))
                .andExpect(jsonPath("$.observeeComments").value("Thanks."))
                .andExpect(jsonPath("$.responses.length()").value(6));
        start(kaia).andExpect(status().isForbidden());
        save(kaia, List.of(answer(1, 1, null))).andExpect(status().isForbidden());
        sign(kaia, "Mine.").andExpect(status().isForbidden());

        // Committee access also covers other schools.
        read(otherSchoolCommittee()).andExpect(status().isOk());
    }

    @Test
    void committeeMemberForwardsACompletedRecordAndItIsLoggedAndNotified() throws Exception {
        start(keira).andExpect(status().isOk());
        forward(kaia, id(dario)).andExpect(status().isConflict());
        submit(keira, complete()).andExpect(status().isOk());
        // Submitted is not locked yet, so there is nothing to forward.
        forward(kaia, id(dario)).andExpect(status().isConflict());
        sign(leander, null).andExpect(status().isOk());

        forward(kaia, id(dario)).andExpect(status().isNoContent());

        var forwarded =
                events().stream().filter(e -> "FORWARDED".equals(e.eventType())).toList();
        assertThat(forwarded).hasSize(1);
        assertThat(forwarded.get(0).actorProfessorId()).isEqualTo(id(kaia));
        assertThat(forwarded.get(0).recipientProfessorId()).isEqualTo(id(dario));
        assertThat(eventsAtNow()).isEqualTo(events().size());
        assertThat(notificationsTo(dario)).hasSize(1);
        // Forwarding is not access: Dario still cannot open the record.
        read(dario).andExpect(status().isForbidden());
    }

    @Test
    void onlyCommitteeMembersCanForwardAndTheRecipientMustExist() throws Exception {
        complete(null);

        for (AccountPrincipal other : List.of(keira, leander, dario))
            forward(other, id(dario)).andExpect(status().isForbidden());
        forward(kaia, 999_999).andExpect(status().isNotFound());
        write(
                        kaia,
                        post("/api/observations/" + observation + "/forward")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(events()).extracting(ObservationAuditLog::eventType).doesNotContain("FORWARDED");
        forward(otherSchoolCommittee(), id(dario)).andExpect(status().isNoContent());
    }

    // Takes the observation from scheduled to completed: the observer starts and submits, the observee signs off.
    private void complete(String observeeComments) throws Exception {
        start(keira).andExpect(status().isOk());
        submit(keira, complete()).andExpect(status().isOk());
        sign(leander, observeeComments).andExpect(status().isOk());
    }

    // A complete form: all five ratings, the required strengths, and no suggestions.
    private List<Answer> complete() {
        return List.of(
                answer(1, 4, "Most students joined the pair discussion."),
                answer(2, 5, null),
                answer(3, 4, "Each step of the worked example was explained aloud."),
                answer(4, 3, null),
                answer(5, 4, null),
                answer(6, null, "Clear explanations and an easy rapport with students."));
    }

    private Answer answer(int displayOrder, Integer rating, String comments) {
        return new Answer(questions.get(displayOrder - 1), rating, comments);
    }

    // Every write in these tests happens at the fixed time, so every log entry should carry it.
    private long eventsAtNow() {
        return events().stream()
                .filter(e -> e.occurredAt().equals(LocalDateTime.parse(NOW)))
                .count();
    }

    private List<ObservationAuditLog> events() {
        return auditLog.findByObservationIdOrderByLogIdAsc(observation);
    }

    // The notifications sent at the test's fixed time; the seeded ones are all older.
    private List<String> notificationsTo(AccountPrincipal principal) {
        return notifications.findByProfessorId(id(principal)).stream()
                .filter(n -> n.createdAt().equals(LocalDateTime.parse(NOW)))
                .map(ObserverNotification::message)
                .toList();
    }

    private ResultActions read(AccountPrincipal principal) throws Exception {
        return mvc.perform(get("/api/observations/" + observation).with(user(principal)));
    }

    private ResultActions start(AccountPrincipal principal) throws Exception {
        return write(principal, post("/api/observations/" + observation + "/start"));
    }

    private ResultActions save(AccountPrincipal principal, List<Answer> answers) throws Exception {
        return write(
                principal,
                put("/api/observations/" + observation + "/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(answers)));
    }

    private ResultActions submit(AccountPrincipal principal, List<Answer> answers) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(answers)));
    }

    private ResultActions sign(AccountPrincipal principal, String comments) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/observee-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ObservationController.SignOffBody(comments))));
    }

    private String observeeStatus() {
        return observations
                .findById(observation)
                .flatMap(o -> signups.findById(o.signupId()))
                .flatMap(signup -> observees.findById(signup.observeeId()))
                .orElseThrow()
                .status();
    }

    private Observation current() {
        return observations.findById(observation).orElseThrow();
    }

    private String statusOf() {
        return current().status();
    }

    private ResultActions forward(AccountPrincipal principal, int recipientProfessorId) throws Exception {
        return write(
                principal,
                post("/api/observations/" + observation + "/forward")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ObservationController.ForwardBody(recipientProfessorId))));
    }

    // Signs the request in and adds a CSRF token the way the frontend gets one (see
    // FacultyObserverRequestIntegrationTests for why the csrf() helper is not used).
    private ResultActions write(AccountPrincipal principal, MockHttpServletRequestBuilder request) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(request.with(user(principal)).cookie(token).header("X-XSRF-TOKEN", token.getValue()));
    }

    private String body(List<Answer> answers) {
        return json.writeValueAsString(new ObservationController.ResponsesBody(answers));
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
