package edu.utdallas.cs4485.assessmenthelper.controller;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.firstTemplateId;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.newObservation;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.professor;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.submitted;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withObservationDeadline;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withPlan;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withSignupDeadline;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.TestFixtures;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
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
    JdbcAggregateTemplate db;

    @Autowired
    MissingObservationService service;

    @Autowired
    MockMvc mvc;

    int professor;

    @BeforeEach
    void requiredProfessor() {
        int department = db.findAll(Department.class).stream()
                .mapToInt(Department::departmentId)
                .min()
                .orElseThrow();
        db.insert(new Professor(
                null,
                department,
                "ASSOCIATE",
                "Missing",
                "Test",
                "missing-test@example.edu",
                LocalDate.parse("2024-09-01"),
                true));
        professor = id("missing-test@example.edu");
    }

    int id(String email) {
        return professor(db, email).professorId();
    }

    void updateProfessor(int id, UnaryOperator<Professor> change) {
        db.update(change.apply(db.findById(id, Professor.class)));
    }

    void updateCycle(UnaryOperator<EvaluationCycle> change) {
        db.update(change.apply(db.findById(1, EvaluationCycle.class)));
    }

    void signUp(String status) {
        Observee observee = db.insert(new Observee(null, professor, 1, "ACTIVE"));
        int section = db.findAll(CourseSection.class).stream()
                .mapToInt(CourseSection::sectionId)
                .min()
                .orElseThrow();
        db.insert(new EvaluationSignup(null, observee.observeeId(), section, null, status));
    }

    void updateObservations(Predicate<Observation> which, UnaryOperator<Observation> change) {
        db.findAll(Observation.class).stream().filter(which).forEach(o -> db.update(change.apply(o)));
    }

    Predicate<Observation> observedBy(int professorId) {
        int observerId = observerOf(db, professorId);
        return o -> o.observerId() == observerId;
    }

    Predicate<Observation> on(int signupId) {
        return o -> o.signupId() == signupId;
    }

    static Professor withHireDate(Professor p, LocalDate hireDate) {
        return new Professor(
                p.professorId(),
                p.departmentId(),
                p.professorRank(),
                p.firstName(),
                p.lastName(),
                p.email(),
                hireDate,
                p.activeStatus());
    }

    void setSignupStatus(int signupId, String status) {
        db.update(withStatus(db.findById(signupId, EvaluationSignup.class), status));
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
        updateCycle(c -> withSignupDeadline(c, LocalDate.parse("2026-11-16")));
        assertThat(has(professor, "MISSING_SIGNUP")).isFalse();
    }

    @Test
    void upcomingAndInactiveProfessorsAreExcluded() {
        updateProfessor(professor, p -> withHireDate(p, LocalDate.parse("2025-09-01")));
        assertThat(service.findByProfessor(professor)).isEmpty();
        updateProfessor(professor, p -> inactive(withHireDate(p, LocalDate.parse("2024-09-01"))));
        assertThat(service.findByProfessor(professor)).isEmpty();
    }

    @Test
    void registeredButUnscheduledEvaluationIsFlagged() {
        signUp("PENDING");
        assertThat(has(professor, "MISSING_SIGNUP")).isFalse();
        assertThat(has(professor, "MISSING_SCHEDULE")).isTrue();
        setObservationDeadline(LocalDate.parse("2026-11-16"));
        assertThat(has(professor, "MISSING_SCHEDULE")).isFalse();
    }

    @Test
    void cancelledSignupDoesNotSatisfyRequirement() {
        signUp("CANCELLED");
        assertThat(has(professor, "MISSING_SIGNUP")).isTrue();
    }

    @Test
    void lateSubmissionBelongsToObserverAndClearsOnSubmission() {
        int observer = id("keira.larkspur@example.edu");
        int observee = id("leander.coldwell@example.edu");
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        assertThat(has(observee, "LATE_SUBMISSION")).isFalse();
        updateObservations(observedBy(observer), o -> submitted(o, LocalDateTime.parse("2026-11-16T09:00:00")));
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        assertThat(has(observee, "MISSING_SIGN_OFF")).isTrue();
    }

    @Test
    void futureAndSameDayObservationsAreMissingAfterTheCycleDeadline() {
        int observer = id("keira.larkspur@example.edu");
        updateObservations(observedBy(observer), o -> withPlan(o, LocalDate.parse("2026-11-16"), o.startTime()));
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        updateObservations(observedBy(observer), o -> withPlan(o, LocalDate.parse("2026-11-17"), o.startTime()));
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
    }

    @Test
    void aStartedButUnsubmittedObservationIsStillLate() {
        int observer = id("keira.larkspur@example.edu");
        updateObservations(observedBy(observer), o -> withStatus(o, "IN_PROGRESS"));
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
    }

    @Test
    void incompleteAlertsUseTheDeadlineAndExcludeCancelledAndCompletedRecords() {
        int observer = id("keira.larkspur@example.edu");
        int observee = id("leander.coldwell@example.edu");
        setObservationDeadline(LocalDate.parse("2026-11-16"));
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        setObservationDeadline(LocalDate.parse("2026-11-15"));
        assertThat(has(observer, "LATE_SUBMISSION")).isTrue();
        setObservationStatus(LEANDER, "SUBMITTED");
        assertThat(has(observee, "MISSING_SIGN_OFF")).isTrue();
        setObservationStatus(LEANDER, "COMPLETED");
        assertThat(has(observee, "MISSING_SIGN_OFF")).isFalse();
        setObservationStatus(LEANDER, "IN_PROGRESS");
        setSignupStatus(signupOf(LEANDER), "CANCELLED");
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        assertThat(has(observee, "MISSING_SUBMISSION")).isFalse();
    }

    @Test
    void anUndatedObservationIsMissingAfterTheDeadline() {
        int observer = id("keira.larkspur@example.edu");
        updateObservations(observedBy(observer), o -> withPlan(o, null, null));
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
        return TestFixtures.signupOf(db, id(email));
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
        updateObservations(on(signupOf(email)), o -> withStatus(o, status));
    }

    void setObservationDeadline(LocalDate deadline) {
        updateCycle(c -> withObservationDeadline(c, deadline));
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
        setSignupStatus(signupOf(AVERY), "CANCELLED");
        assertThat(missingObservees()).hasSize(7);
        assertThat(missingObservees()).doesNotContain("Avery Hazelwood");
    }

    @Test
    void aSignupShowsTheSubmittedObservationAheadOfANewerAcceptedOne() throws Exception {
        int signup = signupOf(LEANDER);
        setObservationStatus(LEANDER, "SUBMITTED");
        var accepted = newObservation(signup, observerOf(db, id(AVERY)), firstTemplateId(db), "ACCEPTED");
        db.insert(withPlan(accepted, LocalDate.parse("2026-11-05"), null));
        assertThat(Collections.frequency(missingObservees(), "Leander Coldwell"))
                .isEqualTo(1);
        String signupPath = "$.signups[?(@.signupId == " + signup + ")]";
        mvc.perform(get(URL).with(user(COMMITTEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(signupPath).value(hasSize(1)))
                .andExpect(jsonPath(signupPath + ".observer").value("Keira Larkspur"))
                .andExpect(jsonPath(signupPath + ".observationStatus").value("SUBMITTED"));
        updateObservations(on(signup).and(o -> "ACCEPTED".equals(o.status())), o -> withStatus(o, "COMPLETED"));
        assertThat(missingObservees()).doesNotContain("Leander Coldwell");
    }

    @Test
    void theObservationDeadlineDayIsStillOnTime() {
        setObservationDeadline(LocalDate.parse("2026-11-16"));
        assertThat(missing()).isEmpty();
    }

    @Test
    void aCycleWithoutAnObservationDeadlineHasNothingMissing() {
        setObservationDeadline(null);
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
