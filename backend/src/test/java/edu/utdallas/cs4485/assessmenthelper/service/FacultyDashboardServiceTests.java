package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Dashboard;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.EvaluationStatus;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.ObservationItem;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.ObservationStatus;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Role;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Schedule;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.TermObservations;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.Upcoming;
import edu.utdallas.cs4485.assessmenthelper.service.FacultyDashboardService.UpcomingType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs on data.sql's Fall 2026 demo (evaluation cycle 1, semester 13; signups close 2026-10-31 and feedback is due
 * 2026-12-01). Dario (34) is due, has not signed up and teaches CS 3345.005 (section 298). Keira (41) is due and
 * signed up, and observes Leander (13) on 2026-11-04. Kaia (12) is a full professor who is not due.
 */
@DataJdbcTest
@Import({
    FacultyDashboardService.class,
    EvaluationEligibilityService.class,
    EvaluationRecordService.class,
    ObserverEligibilityService.class,
    EvaluationSignupService.class,
    ObserverRequestContextRepository.class,
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
    JdbcTemplate db;

    @BeforeEach
    void resetClock() {
        clock.set("2026-10-26");
    }

    @Test
    void theStatusFollowsTheProfessorThroughTheCycle() {
        assertThat(service.dashboard(DARIO).evaluation().status()).isEqualTo(EvaluationStatus.NOT_SIGNED_UP);

        int signup = signups.save(DARIO, FALL_2026, 298, null).signupId();
        var awaiting = service.dashboard(DARIO).evaluation();
        assertThat(awaiting.status()).isEqualTo(EvaluationStatus.AWAITING_OBSERVER);
        assertThat(awaiting.course()).isEqualTo("CS 3345.005");
        assertThat(awaiting.observer()).isNull();

        // Accepting a request records an ACCEPTED observation.
        int observation = observe(signup, ARLO, "2026-11-10");
        var scheduled = service.dashboard(DARIO);
        assertThat(scheduled.evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);
        assertThat(scheduled.evaluation().observer()).isEqualTo("Arlo Glimmerton");
        assertThat(scheduled.evaluation().observationDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(scheduled.termObservations()).isEqualTo(new TermObservations(1, 0, 1));
        assertThat(scheduled.upcoming().getFirst().status()).isEqualTo(ObservationStatus.NOT_STARTED);

        // Arlo starts the form; for Dario it is still the observation he is waiting on.
        clock.set("2026-11-10");
        db.update("UPDATE observation SET status = 'IN_PROGRESS' WHERE observation_id = ?", observation);
        var started = service.dashboard(DARIO);
        assertThat(started.evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);
        assertThat(started.upcoming().getFirst().status()).isEqualTo(ObservationStatus.IN_PROGRESS);

        submit(observation);
        var submitted = service.dashboard(DARIO);
        assertThat(submitted.evaluation().status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(submitted.termObservations()).isEqualTo(new TermObservations(1, 1, 0));
        assertThat(types(submitted)).containsExactly(UpcomingType.FEEDBACK);

        complete(observation, "2026-11-10T11:00:00");
        clock.set("2026-11-11");
        var completed = service.dashboard(DARIO);
        assertThat(completed.evaluation().status()).isEqualTo(EvaluationStatus.COMPLETED);
        assertThat(completed.evaluation().completedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 11, 0));
        assertThat(completed.termObservations()).isEqualTo(new TermObservations(1, 1, 0));
    }

    @Test
    void theSignupReminderShowsThroughTheDeadlineDay() {
        clock.set("2026-10-31");
        assertThat(types(service.dashboard(DARIO))).containsExactly(UpcomingType.SIGNUP);
        assertThat(service.dashboard(DARIO).upcoming().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 31));

        clock.set("2026-11-01");
        var late = service.dashboard(DARIO);
        assertThat(late.evaluation().status()).isEqualTo(EvaluationStatus.NOT_SIGNED_UP);
        assertThat(late.upcoming()).isEmpty();
    }

    @Test
    void anObservationIsOverdueFromTheDayAfterItsDateUntilItIsSubmitted() {
        clock.set("2026-11-04");
        assertThat(firstForMe(LEANDER).overdue()).isFalse();
        assertThat(service.dashboard(LEANDER).upcoming().getFirst().overdue()).isFalse();

        clock.set("2026-11-05");
        assertThat(firstForMe(LEANDER).status()).isEqualTo(ObservationStatus.NOT_STARTED);
        assertThat(firstForMe(LEANDER).overdue()).isTrue();
        assertThat(service.dashboard(LEANDER).upcoming().getFirst().overdue()).isTrue();
        // A late observation is still the professor's open evaluation.
        assertThat(service.dashboard(LEANDER).evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);

        submit(observationOf(LEANDER));
        assertThat(firstForMe(LEANDER).status()).isEqualTo(ObservationStatus.SUBMITTED);
        assertThat(firstForMe(LEANDER).overdue()).isFalse();
    }

    @Test
    void feedbackIsOwedByEachSideUntilGivenAndOverdueAfterTheDeadline() {
        submit(observationOf(LEANDER));
        clock.set("2026-12-01");
        for (int professor : new int[] {LEANDER, KEIRA}) {
            Upcoming feedback = service.dashboard(professor).upcoming().stream()
                    .filter(u -> u.type() == UpcomingType.FEEDBACK)
                    .findFirst()
                    .orElseThrow();
            assertThat(feedback.date()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(feedback.overdue()).isFalse();
            assertThat(feedback.course()).isEqualTo("CS 3345.001");
        }

        db.update("""
                INSERT INTO process_feedback (cycle_id, professor_id, signup_id, submitted_date)
                VALUES (1, ?, (SELECT signup_id FROM observation WHERE observation_id = ?), DATE '2026-12-01')
                """, LEANDER, observationOf(LEANDER));
        assertThat(types(service.dashboard(LEANDER))).doesNotContain(UpcomingType.FEEDBACK);

        clock.set("2026-12-02");
        assertThat(service.dashboard(KEIRA).upcoming())
                .filteredOn(u -> u.type() == UpcomingType.FEEDBACK)
                .singleElement()
                .extracting(Upcoming::overdue)
                .isEqualTo(true);
    }

    @Test
    void undatedObservationsListFirstButComeLastInUpcoming() {
        // Keira's own observation by Arlo has no date yet; her observation of Leander is on 2026-11-04.
        observe(signupOf(KEIRA), ARLO, null);
        var keira = service.dashboard(KEIRA);

        assertThat(keira.observationsForMe())
                .extracting(ObservationItem::semester, ObservationItem::date)
                .containsExactly(tuple("Fall 2026", null), tuple("Fall 2025", LocalDate.of(2025, 10, 28)));
        assertThat(keira.upcoming())
                .extracting(Upcoming::role, Upcoming::date)
                .containsExactly(tuple(Role.OBSERVER, LocalDate.of(2026, 11, 4)), tuple(Role.OBSERVEE, null));
        assertThat(keira.evaluation().status()).isEqualTo(EvaluationStatus.SCHEDULED);
        assertThat(keira.evaluation().observationDate()).isNull();
    }

    @Test
    void aCancelledSignupsObservationLeavesBothDashboards() {
        db.update("UPDATE evaluation_signup SET status = 'CANCELLED' WHERE signup_id = ?", signupOf(LEANDER));

        var keira = service.dashboard(KEIRA);
        assertThat(keira.observationsForOthers()).isEmpty();
        assertThat(keira.upcoming()).isEmpty();
        assertThat(keira.termObservations().total()).isZero();
        assertThat(service.dashboard(LEANDER).observationsForMe()).isEmpty();
    }

    @Test
    void aRankWithoutAnActivePolicyHasNoSchedule() {
        db.update("UPDATE evaluation_policy SET active_status = FALSE WHERE professor_rank = 'FULL'");
        var kaia = service.dashboard(KAIA);

        assertThat(kaia.schedule()).isEqualTo(new Schedule(null, null, null, null, null));
        assertThat(kaia.evaluation().status()).isEqualTo(EvaluationStatus.NOT_DUE);
        // The profile still shows the rank on file.
        assertThat(kaia.profile().rank()).isEqualTo("FULL");
    }

    @Test
    void historyLeavesOutTheCurrentCycle() {
        assertThat(records.findByProfessor(LEANDER))
                .extracting(EvaluationRecord::cycleId)
                .contains(1);
        assertThat(service.dashboard(LEANDER).history()).isEmpty();
        assertThat(service.dashboard(KEIRA).history())
                .extracting(EvaluationRecord::term, EvaluationRecord::academicYear)
                .containsExactly(tuple("Fall", 2025));
    }

    @Test
    void anUnknownProfessorIsNotFound() {
        assertThatThrownBy(() -> service.dashboard(99999)).isInstanceOf(NotFoundException.class);
    }

    private static List<UpcomingType> types(Dashboard dashboard) {
        return dashboard.upcoming().stream().map(Upcoming::type).toList();
    }

    private ObservationItem firstForMe(int professorId) {
        return service.dashboard(professorId).observationsForMe().getFirst();
    }

    private int observe(int signupId, int observerProfessorId, String date) {
        db.update("""
                INSERT INTO observation (signup_id, observer_id, template_id, observation_date, status)
                VALUES (?, (SELECT observer_id FROM observer WHERE professor_id = ? AND cycle_id = 1),
                    (SELECT MIN(template_id) FROM observation_template), ?, 'ACCEPTED')
                """, signupId, observerProfessorId, date == null ? null : LocalDate.parse(date));
        return db.queryForObject("SELECT observation_id FROM observation WHERE signup_id = ?", Integer.class, signupId);
    }

    /** What the observation form's Submit does: the observer's part is over. */
    private void submit(int observationId) {
        db.update(
                "UPDATE observation SET status = 'SUBMITTED', observer_signed = TRUE, observer_signed_at = ? WHERE observation_id = ?",
                LocalDateTime.now(clock),
                observationId);
    }

    private void complete(int observationId, String completedAt) {
        db.update(
                "UPDATE observation SET status = 'COMPLETED', completed_at = ? WHERE observation_id = ?",
                LocalDateTime.parse(completedAt),
                observationId);
    }

    private int signupOf(int professorId) {
        return db.queryForObject("""
                SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id = es.observee_id
                WHERE oe.professor_id = ? AND oe.cycle_id = 1
                """, Integer.class, professorId);
    }

    private int observationOf(int observeeProfessorId) {
        return db.queryForObject(
                "SELECT observation_id FROM observation WHERE signup_id = ?",
                Integer.class,
                signupOf(observeeProfessorId));
    }
}
