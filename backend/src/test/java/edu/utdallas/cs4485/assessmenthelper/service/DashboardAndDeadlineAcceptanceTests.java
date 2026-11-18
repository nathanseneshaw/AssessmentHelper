package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService.Status;
import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService.TrackedObservation;
import edu.utdallas.cs4485.assessmenthelper.service.ObservationService.ViewerRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceptance tests for the dashboards and deadlines: US2 and US3 (rank decides the evaluation interval), US14 and
 * US35.4 (missing observations), US35 and US35.5 (KPIs agree with the lists), and US36 (a professor's own
 * observations). A failure in a "agrees with" test may be a real bug, so compare the two numbers it prints.
 */
@SpringBootTest
@Import(AcceptanceSupport.Time.class)
@Transactional
class DashboardAndDeadlineAcceptanceTests extends AcceptanceSupport {
    // Leander is an assistant professor and Arlo is an associate professor.
    private static final String AFTER_THE_OBSERVATION_DATE = "2026-11-05T16:00:00Z";
    private static final String AFTER_THE_OBSERVATION_DEADLINE = "2026-11-16T16:00:00Z";

    @Autowired
    private AcDashboardService dashboard;

    @Autowired
    private MissingObservationService missing;

    @Autowired
    private EvaluationDeadlineService deadlines;

    @Autowired
    private ProfessorRepository professors;

    private void promoteLeanderToFull() {
        var leander = professors.findById(LEANDER).orElseThrow();
        professors.save(new Professor(
                leander.professorId(),
                leander.departmentId(),
                "FULL",
                leander.firstName(),
                leander.lastName(),
                leander.email(),
                leander.hireDate(),
                leander.activeStatus()));
    }

    private TrackedObservation leandersEvaluation() {
        return dashboard.observations().observations().stream()
                .filter(o -> o.observee().contains("Leander"))
                .findFirst()
                .orElseThrow();
    }

    private List<MissingObservationService.Alert> lateAlerts(int professorId) {
        return missing.findByProfessor(professorId).stream()
                .filter(a -> "LATE_SUBMISSION".equals(a.kind()))
                .toList();
    }

    private Optional<ViewerRole> roleInListOf(int professorId, int obs) {
        return observations.professorObservations(professorId).stream()
                .filter(o -> o.observationId() == obs)
                .findFirst()
                .map(ObservationService.Summary::role);
    }

    // ---------- US2 / US3: the rank decides how often a professor is evaluated ----------

    @Test
    void us2_anAssistantProfessorIsEvaluatedEveryYearAndAnAssociateEveryTwoYears() {
        assertThat(deadlines.findByProfessor(LEANDER).evaluationIntervalYears()).isEqualTo(1);
        assertThat(deadlines.findByProfessor(ARLO).evaluationIntervalYears()).isEqualTo(2);
    }

    @Test
    void us3_aRankChangeChangesTheIntervalAndMovesTheNextDueDateLater() {
        LocalDate asAssistant = deadlines.findByProfessor(LEANDER).nextDueDate();

        promoteLeanderToFull();

        var asFull = deadlines.findByProfessor(LEANDER);
        assertThat(asFull.evaluationIntervalYears()).isEqualTo(2);
        assertThat(asFull.nextDueDate()).isAfter(asAssistant);
    }

    // ---------- US35: KPIs ----------

    @Test
    void us35_theDashboardLoadsForTheCurrentCycle() {
        var metrics = dashboard.metrics();

        assertThat(metrics.cycleLabel()).isEqualTo("Fall 2026");
        assertThat(metrics.listSize()).isPositive();
        assertThat(metrics.participation().count())
                .isBetween(0, metrics.participation().total());
    }

    @Test
    void us35_4_anIncompleteObservationIsMissingOnlyAfterTheCycleDeadline() {
        // The observation date passing is not enough; the cycle deadline is.
        clock.set(AFTER_THE_OBSERVATION_DATE);
        assertThat(leandersEvaluation().status()).isEqualTo(Status.SCHEDULED);
        assertThat(dashboard.metrics().missingObservations()).isZero();

        clock.set(AFTER_THE_OBSERVATION_DEADLINE);

        assertThat(leandersEvaluation().status()).isEqualTo(Status.MISSING);
        assertThat(leandersEvaluation().missingReason()).isEqualTo("Not submitted");
        assertThat(dashboard.metrics().missingObservations()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void us35_5_theMissingCountAgreesWithTheTrackingList() {
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);

        long inList = dashboard.observations().observations().stream()
                .filter(o -> o.status() == Status.MISSING)
                .count();

        assertThat(dashboard.metrics().missingObservations()).isEqualTo((int) inList);
    }

    @Test
    void us35_5_theMissingCountDropsWhenTheObservationIsCompleted() {
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);
        int before = dashboard.metrics().missingObservations();

        complete(observationId(), null);

        assertThat(dashboard.metrics().missingObservations()).isEqualTo(before - 1);
        assertThat(leandersEvaluation().status()).isEqualTo(Status.COMPLETED);
    }

    // ---------- US14: tracking and flagging ----------

    @Test
    void us14_theTrackingListShowsWhoObservesWhoAndFollowsTheSignatures() {
        int obs = observationId();
        var scheduled = leandersEvaluation();
        assertThat(scheduled.observer()).contains("Keira");
        assertThat(scheduled.course()).startsWith("CS 3345");
        assertThat(scheduled.date()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(scheduled.observerSigned()).isFalse();

        keiraSubmits(obs);
        var submitted = leandersEvaluation();
        assertThat(submitted.observerSigned()).isTrue();
        assertThat(submitted.observeeSigned()).isFalse();

        leanderSigns(obs, null);
        var completed = leandersEvaluation();
        assertThat(completed.observeeSigned()).isTrue();
        assertThat(completed.status()).isEqualTo(Status.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void us14_theObserverHasALateAlertFromTheCycleDeadlineUntilTheySubmit() {
        clock.set(AFTER_THE_OBSERVATION_DATE);
        assertThat(lateAlerts(KEIRA)).isEmpty();

        clock.set(AFTER_THE_OBSERVATION_DEADLINE);
        var alerts = lateAlerts(KEIRA);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().observationId()).isEqualTo(observationId());

        // Once submitted, it is the observee's sign-off that is missing.
        keiraSubmits(observationId());
        assertThat(lateAlerts(KEIRA)).isEmpty();
        assertThat(leandersEvaluation().missingReason()).isEqualTo("Not signed");
        assertThat(missing.findByProfessor(LEANDER)).anyMatch(a -> "MISSING_SIGN_OFF".equals(a.kind()));
    }

    // ---------- US36: a professor's own observations ----------

    @Test
    void us36_eachProfessorSeesTheObservationInTheRoleTheyHaveInIt() {
        int obs = observationId();

        assertThat(roleInListOf(KEIRA, obs)).contains(ViewerRole.OBSERVER);
        assertThat(roleInListOf(LEANDER, obs)).contains(ViewerRole.OBSERVEE);
        assertThat(roleInListOf(DARIO, obs)).isEmpty();
    }
}
