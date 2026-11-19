package edu.utdallas.cs4485.assessmenthelper.service;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.completed;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.firstTemplateId;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.newFeedback;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.newObservation;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.signupOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.submitted;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withPlan;
import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Dashboard;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.EvaluationStatus;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.ObservationItem;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.ObservationStatus;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.TermObservations;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Upcoming;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.UpcomingType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Runs on data.sql's Fall 2026 demo (evaluation cycle 1, semester 13; signups close 2026-10-31 and feedback is due
 * 2026-12-01). Dario (34) is due, has not signed up and teaches CS 3345.005 (section 298). Keira (41) is due and
 * signed up, and observes Leander (13) on 2026-11-04. Kaia (12) is a full professor who is not due.
 */
@DataJdbcTest
@Import({
    FacultyDashboardService.class,
    EvaluationEligibilityService.class,
    DisabledNotificationEmailSender.class,
    EvaluationDeadlineService.class,
    EvaluationPolicyService.class,
    EmailTemplateService.class,
    EvaluationRecordService.class,
    ObserverEligibilityService.class,
    EvaluationSignupService.class,
    CycleReadinessService.class,
    FacultyDashboardServiceTests.Time.class
})
class FacultyDashboardServiceTests {
    /** A clock a test can move, so one test can check both sides of a deadline. */
    static class MovableClock extends Clock {
        private Instant instant;

        void set(String date) {
            instant = Instant.parse(date + "T15:00:00Z");
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
        MovableClock clock() {
            return new MovableClock();
        }
    }

    static final int FALL_2026 = 13, KAIA = 12, LEANDER = 13, ARLO = 26, DARIO = 34, KEIRA = 41;

    @Autowired
    FacultyDashboardService service;

    @Autowired
    EvaluationSignupService signups;

    @Autowired
    EvaluationRecordService records;

    @Autowired
    MovableClock clock;

    @Autowired
    JdbcAggregateTemplate db;

    @Autowired
    ObserverEligibilityService matching;

    @Autowired
    EvaluationEligibilityService eligibility;

    @Autowired
    EvaluationDeadlineService deadlines;

    @BeforeEach
    void resetClock() {
        clock.set("2026-10-26");
    }

    @Test
    void theStatusFollowsTheProfessorThroughTheCycle() {
        assertThat(service.dashboard(KAIA).evaluation().status()).isEqualTo(EvaluationStatus.NOT_DUE);
        assertThat(service.dashboard(DARIO).evaluation().status()).isEqualTo(EvaluationStatus.NOT_SIGNED_UP);

        int signup = signups.save(DARIO, FALL_2026, 298, null).signupId();
        var awaiting = service.dashboard(DARIO).evaluation();
        assertThat(awaiting.status()).isEqualTo(EvaluationStatus.AWAITING_OBSERVER);
        assertThat(awaiting.course()).isEqualTo("CS 3345.005");

        // Accepting a request records an ACCEPTED observation.
        int observation = observe(signup, ARLO, "2026-11-10");
        var scheduled = service.dashboard(DARIO);
        assertThat(scheduled.evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);
        assertThat(scheduled.evaluation().observer()).isEqualTo("Arlo Glimmerton");
        assertThat(scheduled.termObservations()).isEqualTo(new TermObservations(1, 0, 1, 0));

        // Once submitted, only the observee is asked to sign off, until the record is complete.
        clock.set("2026-11-10");
        submit(observation);
        var submitted = service.dashboard(DARIO);
        assertThat(submitted.evaluation().status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(submitted.termObservations()).isEqualTo(new TermObservations(1, 0, 0, 1));
        assertThat(types(submitted)).containsExactly(UpcomingType.SIGN_OFF, UpcomingType.FEEDBACK);
        assertThat(types(service.dashboard(ARLO))).doesNotContain(UpcomingType.SIGN_OFF);

        complete(observation, "2026-11-10T11:00:00");
        var completed = service.dashboard(DARIO);
        assertThat(completed.evaluation().status()).isEqualTo(EvaluationStatus.COMPLETED);
        assertThat(completed.evaluation().completedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 11, 0));
        assertThat(types(completed)).doesNotContain(UpcomingType.SIGN_OFF);
    }

    @Test
    void theSignupReminderShowsThroughTheDeadlineDay() {
        clock.set("2026-10-31");
        assertThat(types(service.dashboard(DARIO))).containsExactly(UpcomingType.SIGNUP);

        clock.set("2026-11-01");
        var late = service.dashboard(DARIO);
        assertThat(late.evaluation().status()).isEqualTo(EvaluationStatus.NOT_SIGNED_UP);
        assertThat(late.upcoming()).isEmpty();
    }

    @Test
    void anObservationIsMissingAfterTheCycleDeadlineUntilSigned() {
        clock.set("2026-11-16");
        assertThat(firstForMe(LEANDER).overdue()).isTrue();
        assertThat(service.dashboard(LEANDER).upcoming().getFirst().overdue()).isTrue();
        // A missing observation is still the professor's open evaluation.
        assertThat(service.dashboard(LEANDER).evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);

        // Submitting is not enough: it stays missing until the observee signs off.
        submit(observationOf(LEANDER));
        assertThat(firstForMe(LEANDER).status()).isEqualTo(ObservationStatus.SUBMITTED);
        assertThat(firstForMe(LEANDER).overdue()).isTrue();
    }

    @Test
    void feedbackIsOwedByEachSideUntilGivenAndOverdueAfterTheDeadline() {
        submit(observationOf(LEANDER));
        for (int professor : new int[] {LEANDER, KEIRA}) {
            assertThat(feedbackFor(professor).date()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(feedbackFor(professor).overdue()).isFalse();
        }

        db.insert(newFeedback(1, LEANDER, signupOf(db, LEANDER), null, LocalDate.parse("2026-12-01")));
        assertThat(types(service.dashboard(LEANDER))).doesNotContain(UpcomingType.FEEDBACK);

        clock.set("2026-12-02");
        assertThat(feedbackFor(KEIRA).overdue()).isTrue();
    }

    @Test
    void deferredFacultyAreDueNextSemesterWithoutPretendingTheyCompletedAnEvaluation() {
        int signup = signups.save(DARIO, FALL_2026, 298, null).signupId();
        db.findAll(Observer.class).stream().filter(o -> o.cycleId() == 1).forEach(o -> db.update(inactive(o)));
        clock.set("2026-11-16");
        matching.defer(signup, KAIA, "No matching observers");
        var current = service.dashboard(DARIO);
        assertThat(current.evaluation().status()).isEqualTo(EvaluationStatus.DEFERRED);
        assertThat(current.schedule().dueStatus()).isEqualTo("Due next semester");
        assertThat(current.schedule().nextDueSemester()).isEqualTo("Spring 2027");
        assertThat(deadlines.findByProfessor(DARIO).overdue()).isFalse();
        assertThat(records.findByProfessor(DARIO).getFirst().status()).isEqualTo("DEFERRED");

        db.insert(new EvaluationCycle(
                null,
                14,
                LocalDate.parse("2027-02-01"),
                LocalDate.parse("2027-04-15"),
                LocalDate.parse("2027-05-01"),
                "OPEN"));
        clock.set("2027-01-20");
        assertThat(eligibility.findDue().faculty()).anyMatch(f -> f.professorId() == DARIO);
        assertThat(service.dashboard(DARIO).evaluation().status()).isEqualTo(EvaluationStatus.NOT_SIGNED_UP);
    }

    private static List<UpcomingType> types(Dashboard dashboard) {
        return dashboard.upcoming().stream().map(Upcoming::type).toList();
    }

    private Upcoming feedbackFor(int professorId) {
        return service.dashboard(professorId).upcoming().stream()
                .filter(u -> u.type() == UpcomingType.FEEDBACK)
                .findFirst()
                .orElseThrow();
    }

    private ObservationItem firstForMe(int professorId) {
        return service.dashboard(professorId).observationsForMe().getFirst();
    }

    private int observe(int signupId, int observerProfessorId, String date) {
        var accepted = newObservation(signupId, observerOf(db, observerProfessorId), firstTemplateId(db), "ACCEPTED");
        db.insert(withPlan(accepted, date == null ? null : LocalDate.parse(date), null));
        return observationOn(signupId);
    }

    /** What the observation form's Submit does: the observer's part is over. */
    private void submit(int observationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        updateObservation(observationId, o -> submitted(o, now));
    }

    private void complete(int observationId, String completedAt) {
        updateObservation(observationId, o -> completed(o, LocalDateTime.parse(completedAt)));
    }

    private void updateObservation(int observationId, UnaryOperator<Observation> change) {
        db.update(change.apply(db.findById(observationId, Observation.class)));
    }

    private int observationOf(int observeeProfessorId) {
        return observationOn(signupOf(db, observeeProfessorId));
    }

    private List<Observation> observationsOn(int signupId) {
        return db.findAll(Observation.class).stream()
                .filter(o -> o.signupId() == signupId)
                .toList();
    }

    private int observationOn(int signupId) {
        return only(observationsOn(signupId)).observationId();
    }
}
