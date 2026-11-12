package edu.utdallas.cs4485.assessmenthelper.service;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observationOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.principal;

import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationResponseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverNotificationRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.Role;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.Answer;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationTemplateService.Criterion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

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
    private JdbcAggregateTemplate db;

    @Autowired
    private ObservationRepository observationRecords;

    @Autowired
    private ObservationResponseRepository responses;

    @Autowired
    private ObserverNotificationRepository notifications;

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
        return principal(db, email, schoolId, roles);
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
        return observationOf(db, LEANDER_EMAIL, "Fall", 2026);
    }

    protected List<Criterion> criteria(int observationId) {
        int templateId =
                observationRecords.findById(observationId).orElseThrow().templateId();
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
        return notifications.findByProfessorId(professorId).size();
    }

    protected int storedResponseCount(int observationId) {
        return responses.findByObservationId(observationId).size();
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
