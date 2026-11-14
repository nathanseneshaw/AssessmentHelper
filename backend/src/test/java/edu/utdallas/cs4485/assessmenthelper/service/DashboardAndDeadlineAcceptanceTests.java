package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService.Status;
import edu.utdallas.cs4485.assessmenthelper.service.AcDashboardService.TrackedObservation;
import java.time.LocalDate;
import java.util.List;
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
    // Leander is an assistant professor, Arlo is an associate professor and Kaia is a full professor.
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

    private TrackedObservation leandersEvaluation() {
        return dashboard.observations().observations().stream()
                .filter(o -> o.observee().contains("Leander"))
                .findFirst()
                .orElseThrow();
    }

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

    private List<MissingObservationService.Alert> lateAlerts(int professorId) {
        return missing.findByProfessor(professorId).stream()
                .filter(a -> "LATE_SUBMISSION".equals(a.kind()))
                .toList();
    }

    // ---------- US2 / US3: the rank decides how often a professor is evaluated ----------

    @Test
    void us2_anAssistantProfessorIsEvaluatedEveryYear() {
        assertThat(deadlines.findByProfessor(LEANDER).evaluationIntervalYears()).isEqualTo(1);
        assertThat(deadlines.findByProfessor(LEANDER).professorRank()).isEqualTo("ASSISTANT");
    }

    @Test
    void us2_anAssociateProfessorIsEvaluatedEveryTwoYears() {
        assertThat(deadlines.findByProfessor(ARLO).evaluationIntervalYears()).isEqualTo(2);
        assertThat(deadlines.findByProfessor(ARLO).professorRank()).isEqualTo("ASSOCIATE");
    }

    @Test
    void us2_aFullProfessorIsEvaluatedEveryTwoYears() {
        assertThat(deadlines.findByProfessor(KAIA).evaluationIntervalYears()).isEqualTo(2);
        assertThat(deadlines.findByProfessor(KAIA).professorRank()).isEqualTo("FULL");
    }

    @Test
    void us3_changingARankChangesTheEvaluationInterval() {
        assertThat(deadlines.findByProfessor(LEANDER).evaluationIntervalYears()).isEqualTo(1);

        promoteLeanderToFull();

        assertThat(deadlines.findByProfessor(LEANDER).evaluationIntervalYears()).isEqualTo(2);
        assertThat(deadlines.findByProfessor(LEANDER).professorRank()).isEqualTo("FULL");
    }

    @Test
    void us3_aRankChangeMovesTheNextDueDateLater() {
        LocalDate asAssistant = deadlines.findByProfessor(LEANDER).nextDueDate();

        promoteLeanderToFull();
        LocalDate asFull = deadlines.findByProfessor(LEANDER).nextDueDate();

        assertThat(asAssistant).isNotNull();
        assertThat(asFull).isAfter(asAssistant);
    }

    @Test
    void us2_aProfessorWhoDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> deadlines.findByProfessor(999_999)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void us8_theNextDueDateIsAlwaysAfterTheLastCompletedEvaluation() {
        for (int professor : List.of(LEANDER, ARLO, KAIA, KEIRA, DARIO)) {
            var d = deadlines.findByProfessor(professor);
            if (d.lastEvaluationDate() != null && d.nextDueDate() != null)
                assertThat(d.nextDueDate()).as("professor %d", professor).isAfter(d.lastEvaluationDate());
        }
    }

    // ---------- US35: KPIs ----------

    @Test
    void us35_theDashboardLoadsForTheCurrentCycle() {
        var metrics = dashboard.metrics();

        assertThat(metrics.cycleLabel()).isEqualTo("Fall 2026");
        assertThat(metrics.facultyDue()).isNotNegative();
        assertThat(metrics.listSize()).isPositive();
    }

    @Test
    void us35_everyRateIsAPartOfItsTotal() {
        var metrics = dashboard.metrics();

        assertThat(metrics.participation().count())
                .isBetween(0, metrics.participation().total());
        assertThat(metrics.observerUtilization().count())
                .isBetween(0, metrics.observerUtilization().total());
        assertThat(metrics.listSufficiency().count())
                .isBetween(0, metrics.listSufficiency().total());
        assertThat(metrics.missingObservations()).isNotNegative();
    }

    @Test
    void us35_noObservationIsMissingOnTheDayItIsHeld() {
        assertThat(dashboard.metrics().missingObservations()).isZero();
        assertThat(leandersEvaluation().status()).isEqualTo(Status.SCHEDULED);
    }

    @Test
    void us35_4_anIncompleteObservationIsMissingOnlyAfterTheCycleDeadline() {
        clock.set(AFTER_THE_OBSERVATION_DATE);
        assertThat(leandersEvaluation().status()).isEqualTo(Status.SCHEDULED);
        assertThat(dashboard.metrics().missingObservations()).isZero();
        clock.set("2026-11-15T16:00:00Z");
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
                .filter(o -> o.status() == Status.MISSING && o.observerId() != null)
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
    void us14_theTrackingListShowsWhoObservesWhoAndWhen() {
        var tracked = leandersEvaluation();

        assertThat(tracked.observer()).contains("Keira");
        assertThat(tracked.course()).startsWith("CS 3345");
        assertThat(tracked.date()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(tracked.observeeSigned()).isFalse();
        assertThat(tracked.observerSigned()).isFalse();
    }

    @Test
    void us14_theTrackingListFollowsTheSignaturesAsTheyHappen() {
        int obs = observationId();

        keiraSubmits(obs);
        var afterObserver = leandersEvaluation();
        assertThat(afterObserver.observerSigned()).isTrue();
        assertThat(afterObserver.observeeSigned()).isFalse();
        assertThat(afterObserver.status()).isEqualTo(Status.PENDING_SIGN_OFF);

        leanderSigns(obs, null);
        var afterObservee = leandersEvaluation();
        assertThat(afterObservee.observerSigned()).isTrue();
        assertThat(afterObservee.observeeSigned()).isTrue();
        assertThat(afterObservee.status()).isEqualTo(Status.COMPLETED);
        assertThat(afterObservee.completedAt()).isNotNull();
    }

    @Test
    void us14_aCompletedObservationIsNeverFlaggedMissingEvenAfterTheDeadline() {
        complete(observationId(), null);
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);

        assertThat(leandersEvaluation().status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void us14_theObserverIsToldTheirObservationIsMissingAfterTheCycleDeadline() {
        assertThat(lateAlerts(KEIRA)).isEmpty();

        clock.set(AFTER_THE_OBSERVATION_DATE);
        assertThat(lateAlerts(KEIRA)).isEmpty();
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);
        assertThat(lateAlerts(KEIRA)).hasSize(1);
        assertThat(lateAlerts(KEIRA).getFirst().observationId()).isEqualTo(observationId());
        assertThat(lateAlerts(KEIRA).getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(lateAlerts(KEIRA).getFirst().message()).contains("Leander Coldwell");
    }

    @Test
    void us14_theLateAlertGoesAwayOnceTheObserverSubmits() {
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);
        assertThat(lateAlerts(KEIRA)).hasSize(1);

        keiraSubmits(observationId());

        assertThat(lateAlerts(KEIRA)).isEmpty();
        assertThat(leandersEvaluation().status()).isEqualTo(Status.MISSING);
        assertThat(leandersEvaluation().missingReason()).isEqualTo("Not signed");
        assertThat(missing.findByProfessor(LEANDER)).anyMatch(a -> "MISSING_SIGN_OFF".equals(a.kind()));
    }

    @Test
    void us14_theObserveeIsNotGivenALateSubmissionAlertForSomeoneElsesForm() {
        clock.set(AFTER_THE_OBSERVATION_DEADLINE);

        assertThat(lateAlerts(LEANDER)).isEmpty();
        assertThat(lateAlerts(DARIO)).isEmpty();
    }

    // ---------- US36: a professor's own observations ----------

    @Test
    void us36_theObserverSeesTheObservationAsOneTheyObserve() {
        var mine = observations.professorObservations(KEIRA).stream()
                .filter(o -> o.observationId() == observationId())
                .findFirst()
                .orElseThrow();

        assertThat(mine.role()).isEqualTo(ObservationService.ViewerRole.OBSERVER);
        assertThat(mine.observeeName()).isEqualTo("Leander Coldwell");
        assertThat(mine.status()).isIn(ObservationService.Status.ACCEPTED, ObservationService.Status.SCHEDULED);
    }

    @Test
    void us36_theObserveeSeesTheObservationAsOneTheyReceive() {
        var mine = observations.professorObservations(LEANDER).stream()
                .filter(o -> o.observationId() == observationId())
                .findFirst()
                .orElseThrow();

        assertThat(mine.role()).isEqualTo(ObservationService.ViewerRole.OBSERVEE);
        assertThat(mine.observerName()).isEqualTo("Keira Larkspur");
    }

    @Test
    void us36_aProfessorNotInvolvedDoesNotSeeItInTheirList() {
        assertThat(observations.professorObservations(DARIO)).noneMatch(o -> o.observationId() == observationId());
    }

    @Test
    void us36_theListShowsTheNewStatusAfterEachStep() {
        int obs = observationId();

        keiraStarts(obs);
        assertThat(statusInListOf(LEANDER, obs)).isEqualTo(ObservationService.Status.IN_PROGRESS);

        observations.submit(obs, completeAnswers(obs), keira());
        assertThat(statusInListOf(LEANDER, obs)).isEqualTo(ObservationService.Status.SUBMITTED);

        leanderSigns(obs, null);
        assertThat(statusInListOf(LEANDER, obs)).isEqualTo(ObservationService.Status.COMPLETED);
        assertThat(statusInListOf(KEIRA, obs)).isEqualTo(ObservationService.Status.COMPLETED);
    }

    private ObservationService.Status statusInListOf(int professorId, int obs) {
        return observations.professorObservations(professorId).stream()
                .filter(o -> o.observationId() == obs)
                .findFirst()
                .orElseThrow()
                .status();
    }
}
