package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.MissingObservationService;
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
    }

    @Test
    void futureAndSameDayObservationsAreNotLate() {
        int observer = id("keira.larkspur@example.edu");
        db.update("""
                UPDATE observation SET observation_date=DATE '2026-11-16'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
        db.update("""
                UPDATE observation SET observation_date=DATE '2026-11-17'
                WHERE observer_id IN (SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1)
                """, observer);
        assertThat(has(observer, "LATE_SUBMISSION")).isFalse();
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
}
