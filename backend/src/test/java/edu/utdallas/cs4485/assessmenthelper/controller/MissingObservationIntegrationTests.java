package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MissingObservationIntegrationTests.FixedTime.class)
@Transactional
class MissingObservationIntegrationTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock missingClock() {
            return Clock.fixed(Instant.parse("2026-11-16T16:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired
    JdbcTemplate db;

    @Autowired
    MissingObservationService service;

    @Autowired
    MockMvc mvc;

    int professor;

    @BeforeEach
    void requiredProfessor() {
        db.update("""
                INSERT INTO professor (department_id, professor_rank, first_name, last_name, email, hire_date)
                SELECT MIN(department_id), 'ASSOCIATE', 'Missing', 'Test', 'missing-test@example.edu', DATE '2024-09-01'
                FROM department
                """);
        professor = id("missing-test@example.edu");
    }

    int id(String email) {
        return db.queryForObject("SELECT professor_id FROM professor WHERE email=?", Integer.class, email);
    }

    boolean has(int id, String kind) {
        return service.findByProfessor(id).stream().anyMatch(a -> a.kind().equals(kind));
    }

    @Test
    void requiredProfessorMissingSignupIsFlagged() {
        assertThat(has(professor, "MISSING_SIGNUP")).isTrue();
    }

    @Test
    void signupDeadlineIsInclusive() {
        db.update("UPDATE evaluation_cycle SET signup_deadline=DATE '2026-11-16' WHERE cycle_id=1");
        assertThat(has(professor, "MISSING_SIGNUP")).isFalse();
    }

    @Test
    void upcomingAndInactiveProfessorsAreExcluded() {
        db.update("UPDATE professor SET hire_date=DATE '2025-09-01' WHERE professor_id=?", professor);
        assertThat(service.findByProfessor(professor)).isEmpty();
        db.update(
                "UPDATE professor SET hire_date=DATE '2024-09-01', active_status=FALSE WHERE professor_id=?",
                professor);
        assertThat(service.findByProfessor(professor)).isEmpty();
    }

    @Test
    void registeredButUnscheduledEvaluationIsFlagged() {
        db.update("INSERT INTO observee (professor_id,cycle_id,status) VALUES (?,1,'ACTIVE')", professor);
        db.update("""
                INSERT INTO evaluation_signup (observee_id,section_id,status)
                SELECT oe.observee_id, (SELECT MIN(section_id) FROM course_section), 'PENDING'
                FROM observee oe WHERE oe.professor_id=? AND oe.cycle_id=1
                """, professor);
        assertThat(has(professor, "MISSING_SIGNUP")).isFalse();
        assertThat(has(professor, "MISSING_SCHEDULE")).isTrue();
        db.update("UPDATE evaluation_cycle SET observation_deadline=DATE '2026-11-16' WHERE cycle_id=1");
        assertThat(has(professor, "MISSING_SCHEDULE")).isFalse();
    }

    @Test
    void cancelledSignupDoesNotSatisfyRequirement() {
        db.update("INSERT INTO observee (professor_id,cycle_id,status) VALUES (?,1,'ACTIVE')", professor);
        db.update("""
                INSERT INTO evaluation_signup (observee_id,section_id,status)
                SELECT oe.observee_id, (SELECT MIN(section_id) FROM course_section), 'CANCELLED'
                FROM observee oe WHERE oe.professor_id=? AND oe.cycle_id=1
                """, professor);
        assertThat(has(professor, "MISSING_SIGNUP")).isTrue();
    }

    @Test
    void lateSubmissionBelongsToObserverAndClearsOnSubmission() {
        int observer = id("keira.larkspur@example.edu");
        int observee = id("leander.coldwell@example.edu");
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        assertThat(has(observee, "LATE_SUBMISSION")).isFalse();
        db.update("""
                UPDATE observation SET status='SUBMITTED', observer_signed_at=TIMESTAMP '2026-11-16 09:00:00'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        assertThat(has(observee, "MISSING_SIGN_OFF")).isTrue();
    }

    @Test
    void futureAndSameDayObservationsAreMissingAfterTheCycleDeadline() {
        int observer = id("keira.larkspur@example.edu");
        db.update("""
                UPDATE observation SET observation_date=DATE '2026-11-16'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        db.update("""
                UPDATE observation SET observation_date=DATE '2026-11-17'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
    }

    @Test
    void aStartedButUnsubmittedObservationIsStillLate() {
        int observer = id("keira.larkspur@example.edu");
        db.update("""
                UPDATE observation SET status='IN_PROGRESS'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
    }

    @Test
    void incompleteAlertsUseTheDeadlineAndExcludeCancelledAndCompletedRecords() {
        int observer = id("keira.larkspur@example.edu");
        int observee = id("leander.coldwell@example.edu");
        db.update("UPDATE evaluation_cycle SET observation_deadline=DATE '2026-11-16' WHERE cycle_id=1");
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        db.update("UPDATE evaluation_cycle SET observation_deadline=DATE '2026-11-15' WHERE cycle_id=1");
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        db.update("UPDATE observation SET status='SUBMITTED' WHERE signup_id=?", signupOf(LEANDER));
        assertThat(has(observee, "MISSING_SIGN_OFF")).isTrue();
        db.update("UPDATE observation SET status='COMPLETED' WHERE signup_id=?", signupOf(LEANDER));
        assertThat(has(observee, "MISSING_SIGN_OFF")).isFalse();
        db.update("UPDATE observation SET status='IN_PROGRESS' WHERE signup_id=?", signupOf(LEANDER));
        db.update("UPDATE evaluation_signup SET status='CANCELLED' WHERE signup_id=?", signupOf(LEANDER));
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        assertThat(has(observee, "MISSING_SUBMISSION")).isFalse();
    }

    @Test
    void anUndatedObservationIsMissingAfterTheDeadline() {
        int observer = id("keira.larkspur@example.edu");
        db.update("""
                UPDATE observation SET observation_date=NULL, start_time=NULL
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
    }

    AccountPrincipal principal() {
        return new AccountPrincipal(
                new CurrentUser(999, professor, 1, "Missing Test", "missing-test@example.edu", List.of(Role.PROFESSOR)),
                "unused",
                true);
    }

    @Test
    void apiAllowsOwnerAndDeniesAnotherProfessor() throws Exception {
        mvc.perform(get("/api/professors/" + professor + "/missing-observations")
                        .with(user(principal())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/professors/" + id("keira.larkspur@example.edu") + "/missing-observations")
                        .with(user(principal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadAlerts() throws Exception {
        mvc.perform(get("/api/professors/" + professor + "/missing-observations"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/missing-observations. data.sql's Fall 2026 cycle has eight signups and only Leander's has an
    // observation: Keira's, SCHEDULED. The clock is a day past the
    // 2026-11-15 observation deadline.

    private static final String URL = "/api/missing-observations";
    private static final String LEANDER = "leander.coldwell@example.edu";
    private static final String AVERY = "avery.hazelwood@example.edu";

    private static final AccountPrincipal COMMITTEE = new AccountPrincipal(
            new CurrentUser(
                    1, 12, 1, "Kaia Clearbrook", "kaia.clearbrook@example.edu", List.of(Role.AC, Role.PROFESSOR)),
            null,
            true);

    int signupOf(String email) {
        return db.queryForObject("""
                SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                JOIN professor p ON p.professor_id=oe.professor_id WHERE p.email=? AND oe.cycle_id=1
                """, Integer.class, email);
    }

    List<MissingObservationService.MissingSignup> missing() {
        return service.findMissingSignups().signups();
    }

    List<String> missingObservees() {
        var names = new ArrayList<String>();
        for (var signup : missing()) {
            names.add(signup.observee());
        }
        return names;
    }

    MissingObservationService.MissingSignup missingFor(String email) {
        int signupId = signupOf(email);
        for (var signup : missing()) {
            if (signup.signupId() == signupId) {
                return signup;
            }
        }
        throw new AssertionError("Not listed as missing: " + email);
    }

    void setObservationStatus(String email, String status) {
        db.update("UPDATE observation SET status=? WHERE signup_id=?", status, signupOf(email));
    }

    @Test
    void everySignupWithoutACompletedObservationIsListedAfterTheDeadline() {
        var result = service.findMissingSignups();
        assertThat(result.cycleId()).isEqualTo(1);
        assertThat(result.cycleLabel()).isEqualTo("Fall 2026");
        assertThat(result.observationDeadline()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(missingObservees()).hasSize(8);
        assertThat(missingObservees()).contains("Leander Coldwell", "Avery Hazelwood");
    }

    @Test
    void aSignupWithAnObservationShowsItsObserverAndProgress() {
        var leander = missingFor(LEANDER);
        assertThat(leander.observer()).isEqualTo("Keira Larkspur");
        assertThat(leander.observationStatus()).isEqualTo("SCHEDULED");
        assertThat(leander.course()).startsWith("CS 3345.");
    }

    @Test
    void aSignupNobodyAcceptedHasNoObservationDetails() {
        var avery = missingFor(AVERY);
        assertThat(avery.observer()).isNull();
        assertThat(avery.observationStatus()).isNull();
    }

    @Test
    void aCompletedObservationTakesTheSignupOffTheList() {
        setObservationStatus(LEANDER, "COMPLETED");
        assertThat(missingObservees()).hasSize(7);
        assertThat(missingObservees()).doesNotContain("Leander Coldwell");
    }

    @Test
    void aSubmittedObservationIsStillMissingUntilTheObserveeSignsOff() {
        setObservationStatus(LEANDER, "SUBMITTED");
        assertThat(missingFor(LEANDER).observationStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void aCancelledSignupIsNotListed() {
        db.update("UPDATE evaluation_signup SET status='CANCELLED' WHERE signup_id=?", signupOf(AVERY));
        assertThat(missingObservees()).hasSize(7);
        assertThat(missingObservees()).doesNotContain("Avery Hazelwood");
    }

    @Test
    void aSignupShowsTheSubmittedObservationAheadOfANewerAcceptedOne() throws Exception {
        int signup = signupOf(LEANDER);
        setObservationStatus(LEANDER, "SUBMITTED");
        db.update("""
                INSERT INTO observation (signup_id, observer_id, template_id, observation_date, status)
                SELECT ?, ob.observer_id, (SELECT MIN(template_id) FROM observation_template), DATE '2026-11-05',
                'ACCEPTED'
                FROM observer ob JOIN professor p ON p.professor_id=ob.professor_id
                WHERE p.email=? AND ob.cycle_id=1
                """, signup, AVERY);
        assertThat(Collections.frequency(missingObservees(), "Leander Coldwell"))
                .isEqualTo(1);
        String signupPath = "$.signups[?(@.signupId == " + signup + ")]";
        mvc.perform(get(URL).with(user(COMMITTEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(signupPath).value(hasSize(1)))
                .andExpect(jsonPath(signupPath + ".observer").value("Keira Larkspur"))
                .andExpect(jsonPath(signupPath + ".observationStatus").value("SUBMITTED"));
        db.update("UPDATE observation SET status='COMPLETED' WHERE signup_id=? AND status='ACCEPTED'", signup);
        assertThat(missingObservees()).doesNotContain("Leander Coldwell");
    }

    @Test
    void theObservationDeadlineDayIsStillOnTime() {
        db.update("UPDATE evaluation_cycle SET observation_deadline=DATE '2026-11-16' WHERE cycle_id=1");
        assertThat(missing()).isEmpty();
    }

    @Test
    void aCycleWithoutAnObservationDeadlineHasNothingMissing() {
        db.update("UPDATE evaluation_cycle SET observation_deadline=NULL WHERE cycle_id=1");
        assertThat(service.findMissingSignups().observationDeadline()).isNull();
        assertThat(missing()).isEmpty();
    }

    @Test
    void committeeGetsTheMissingSignupsAsJson() throws Exception {
        mvc.perform(get(URL).with(user(COMMITTEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").value(1))
                .andExpect(jsonPath("$.observationDeadline").value("2026-11-15"))
                .andExpect(jsonPath("$.signups", hasSize(8)));
    }

    @Test
    void professorsAndAnonymousCannotReadTheMissingSignups() throws Exception {
        mvc.perform(get(URL).with(user(principal()))).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
