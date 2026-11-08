package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.CurrentUser;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared setup for the acceptance tests that run on data.sql's Fall 2026 demo.
 *
 * <p>Keira (41) observes Leander's (13) CS 3345.001 on 2026-11-04 at 08:30, and has not started the form. Kaia (12) is
 * on the Assessment Committee in Leander's school (1). Dario (34) has no part in that observation. Every test starts at
 * 10:15:42 in Dallas on 2026-11-04 and may move the clock.
 *
 * <p>Each concrete test class repeats {@code @SpringBootTest}, {@code @Import(AcceptanceSupport.Time.class)} and
 * {@code @Transactional}, so every test rolls back and the seed data is untouched.
 */
abstract class AcceptanceSupport {
    static final String DEFAULT_NOW = "2026-11-04T16:15:42Z";
    static final int KAIA = 12, LEANDER = 13, ARLO = 26, DARIO = 34, KEIRA = 41;
    static final String KAIA_EMAIL = "kaia.clearbrook@example.edu";
    static final String KEIRA_EMAIL = "keira.larkspur@example.edu";
    static final String LEANDER_EMAIL = "leander.coldwell@example.edu";
    static final String DARIO_EMAIL = "dario.hollowell@example.edu";
    static final String OUTSIDER_EMAIL = "isla.wrenfield@example.edu";

    /** A clock a test can move. Time is shown in Dallas. */
    static class MovableClock extends Clock {
        private Instant instant = Instant.parse(DEFAULT_NOW);

        void set(String isoInstant) {
            instant = Instant.parse(isoInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Chicago");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class Time {
        @Bean
        @Primary
        MovableClock acceptanceClock() {
            return new MovableClock();
        }
    }

    @Autowired
    protected JdbcTemplate db;

    @Autowired
    protected ObservationService observations;

    @Autowired
    protected ObservationTemplateService templates;

    @Autowired
    protected ObservationAuditService audit;

    @Autowired
    protected MovableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(DEFAULT_NOW);
    }

    // ---------- people ----------

    /** A logged-in professor, looked up by email. Roles decide whether they count as committee members. */
    protected AccountPrincipal as(String email, int schoolId, Role... roles) {
        Map<String, Object> professor = db.queryForMap(
                "SELECT professor_id, first_name || ' ' || last_name AS name FROM professor WHERE email = ?", email);
        return new AccountPrincipal(
                new CurrentUser(
                        null,
                        ((Number) professor.get("PROFESSOR_ID")).intValue(),
                        schoolId,
                        (String) professor.get("NAME"),
                        email,
                        List.of(roles)),
                null,
                true);
    }

    protected AccountPrincipal keira() {
        return as(KEIRA_EMAIL, 1, Role.PROFESSOR);
    }

    protected AccountPrincipal leander() {
        return as(LEANDER_EMAIL, 1, Role.PROFESSOR);
    }

    protected AccountPrincipal dario() {
        return as(DARIO_EMAIL, 1, Role.PROFESSOR);
    }

    protected AccountPrincipal kaia() {
        return as(KAIA_EMAIL, 1, Role.AC, Role.PROFESSOR);
    }

    /** A committee member from a different school than the observee. */
    protected AccountPrincipal outsiderCommittee() {
        return as(OUTSIDER_EMAIL, 2, Role.AC, Role.PROFESSOR);
    }

    // ---------- the seeded observation ----------

    protected int observationId() {
        return db.queryForObject("""
                SELECT o.observation_id FROM observation o
                JOIN evaluation_signup es ON es.signup_id = o.signup_id JOIN observee oe ON oe.observee_id = es.observee_id
                JOIN professor p ON p.professor_id = oe.professor_id
                JOIN evaluation_cycle c ON c.cycle_id = oe.cycle_id JOIN semester s ON s.semester_id = c.semester_id
                WHERE p.email = 'leander.coldwell@example.edu' AND s.term = 'Fall' AND s.academic_year = 2026
                """, Integer.class);
    }

    protected List<Criterion> criteria(int observationId) {
        int templateId = db.queryForObject(
                "SELECT template_id FROM observation WHERE observation_id = ?", Integer.class, observationId);
        return templates.template(templateId).criteria();
    }

    /** An answer to every question on the form: the lowest rating allowed, and comments on text questions. */
    protected List<Answer> completeAnswers(int observationId) {
        return criteria(observationId).stream()
                .map(q -> "RATING".equals(q.questionType())
                        ? new Answer(
                                q.criterionId(), q.ratingMin() != null ? q.ratingMin() : 1, "Students stayed engaged.")
                        : new Answer(q.criterionId(), null, "Clear pacing and good use of the whiteboard."))
                .toList();
    }

    protected Criterion firstRatingQuestion(int observationId) {
        return criteria(observationId).stream()
                .filter(q -> "RATING".equals(q.questionType()))
                .findFirst()
                .orElseThrow();
    }

    protected int notificationCount(int professorId) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM observer_notification WHERE professor_id = ?", Integer.class, professorId);
    }

    protected int storedResponseCount(int observationId) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM observation_response WHERE observation_id = ?", Integer.class, observationId);
    }

    // ---------- workflow steps ----------

    protected ObservationService.Detail keiraStarts(int observationId) {
        return observations.start(observationId, keira());
    }

    protected ObservationService.Detail keiraSubmits(int observationId) {
        observations.start(observationId, keira());
        return observations.submit(observationId, completeAnswers(observationId), keira());
    }

    protected ObservationService.Detail leanderSigns(int observationId, String comments) {
        return observations.observeeSign(observationId, comments, leander());
    }

    /** The whole workflow: Keira submits and Leander signs off, which completes and locks the record. */
    protected ObservationService.Detail complete(int observationId, String observeeComments) {
        keiraSubmits(observationId);
        return leanderSigns(observationId, observeeComments);
    }
}
