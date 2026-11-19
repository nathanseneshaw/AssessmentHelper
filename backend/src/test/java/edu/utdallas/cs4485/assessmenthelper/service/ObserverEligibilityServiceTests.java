package edu.utdallas.cs4485.assessmenthelper.service;

import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.inactive;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.observerOf;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.only;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.professor;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withDepartment;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withReleasedAt;
import static edu.utdallas.cs4485.assessmenthelper.TestFixtures.withSignupDeadline;
import static org.assertj.core.api.Assertions.*;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverListState;
import edu.utdallas.cs4485.assessmenthelper.entity.ObserverRequest;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverMatchRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Runs on data.sql's Fall 2026 demo. Dario (34) teaches CS 3345.005 (section 298, TR 13:00-14:15) and has not signed
 * up; the signed-up pool is Leander (13), Arlo (26), Avery (31), Keira (41), Xiomara (54) and Perrin (17), and
 * Xiomara and Perrin teach at the same time as Dario. Hollis (9) and Ansel (2) teach only CS 2336 and have not
 * signed up.
 */
@DataJdbcTest
@Import({
    ObserverEligibilityService.class,
    EvaluationSignupService.class,
    ObserverRequestService.class,
    ObserverEligibilityServiceTests.Time.class
})
class ObserverEligibilityServiceTests {
    @TestConfiguration
    static class Time {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    static final int FALL_2026 = 13, DARIO = 34, HOLLIS = 9, ANSEL = 2;
    static final int LEANDER = 13, ARLO = 26, AVERY = 31, KEIRA = 41;
    static final int KEIRA_SIGNUP = 32;

    @Autowired
    ObserverEligibilityService service;

    @Autowired
    EvaluationSignupService signups;

    @Autowired
    ObserverRequestService requests;

    @Autowired
    JdbcAggregateTemplate db;

    @Autowired
    ObserverRepository observers;

    @Autowired
    ObserverMatchRepository matches;

    @Autowired
    ObserverRequestRepository observerRequests;

    @Autowired
    ObservationRepository observations;

    @Autowired
    TeachingHistoryRepository teaching;

    @Test
    void filtersDepartmentLevelActivitySemesterAndTeachingOverlap() {
        clearPriorTeaching(HOLLIS);
        moveToDepartment(ARLO, 2);
        db.update(inactive(db.findById(AVERY, Professor.class)));
        observers.findByProfessorIdAndCycleId(KEIRA, 1).forEach(o -> db.update(inactive(o)));
        // Hollis teaches only 2000-level courses; Ansel's CS 3345 section is in Spring 2027.
        signup(HOLLIS, 284);
        db.insert(new TeachingHistory(null, ANSEL, 322));
        signup(ANSEL, 290);
        int dario = darioSignsUp();
        // Xiomara and Perrin are left out because they teach while Dario does.
        assertThat(candidateProfessors(dario)).containsExactly(LEANDER);
    }

    @Test
    void onlyTeachingFromTheLastTwoYearsQualifies() {
        clearPriorTeaching(HOLLIS);
        LocalDate start = service.cycle().startDate();
        historicalCourse(HOLLIS, start.minusYears(3), start.minusYears(2).minusDays(1));
        signup(HOLLIS, 284);
        int dario = darioSignsUp();
        assertThat(eligibleProfessors(dario)).doesNotContain(HOLLIS);
        historicalCourse(HOLLIS, start.minusYears(1), start.minusMonths(8));
        assertThat(eligibleProfessors(dario)).contains(HOLLIS);
    }

    @Test
    void listsAreCappedAtFiveStayStableAndReleasedListsStopGrowing() {
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).containsExactlyInAnyOrder(LEANDER, ARLO, AVERY, KEIRA);
        assertThat(candidateProfessors(KEIRA_SIGNUP)).hasSize(5).isSubsetOf(eligibleProfessors(KEIRA_SIGNUP));
        var darioIds = candidateIds(dario);
        service.generate();
        assertThat(candidateIds(dario)).containsExactlyElementsOf(darioIds);
        releaseAfterDeadline(dario);
        // Hollis becomes eligible by teaching a CS 3345 section (TR 17:30), but Dario's list has been sent.
        db.insert(new TeachingHistory(null, HOLLIS, 300));
        signup(HOLLIS, 284);
        assertThat(candidateIds(dario)).containsExactlyElementsOf(darioIds);
    }

    @Test
    void anObserverCannotObserveThemselves() {
        // Signing up puts Dario in the observer pool too.
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).doesNotContain(DARIO);
        releaseAfterDeadline(dario);
        assertThatThrownBy(() -> requests.create(dario, observerOf(db, DARIO), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void parsesMeetingDaysAndCourseLevels() {
        assertThat(ObserverEligibilityService.days("TR"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        assertThat(ObserverEligibilityService.days("Monday / Wednesday"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        // A schedule it cannot read means no known meetings, never a guess.
        assertThat(ObserverEligibilityService.days("TBA")).isEmpty();
        assertThat(ObserverEligibilityService.level("3345")).isEqualTo(3000);
        assertThat(ObserverEligibilityService.level("3345L")).isEqualTo(-1);
    }

    @Test
    void signupWindowsStayInsideTheObservationPeriodAndLockOnceCandidatesExist() {
        assertThat(signups.window(DARIO, FALL_2026))
                .isEqualTo(new ObserverEligibilityService.Window(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), null, LocalDate.of(2026, 10, 31), true));
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        LocalDate end = LocalDate.of(2026, 11, 12);
        signups.save(DARIO, FALL_2026, 298, end);
        assertThat(signups.window(DARIO, FALL_2026).end()).isEqualTo(end);
        // Candidates exist, so the selection is locked.
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 11, 10)))
                .isInstanceOf(ConflictException.class);
        signupDeadline("2026-10-20");
        assertThatThrownBy(() -> signup(HOLLIS, 288)).isInstanceOf(ConflictException.class);
    }

    @Test
    void releaseThenMultipleRequestsWithDeclineAndAccept() {
        int target = darioSignsUp();
        var candidates = list(target).candidates();
        assertThat(candidates).hasSize(4);
        int first = candidates.get(0).observerId(), second = candidates.get(1).observerId();
        int firstProfessor = candidates.get(0).professorId(),
                secondProfessor = candidates.get(1).professorId();

        // Dario cannot request until the committee releases the list, which waits for the signup deadline.
        assertThat(options(DARIO).candidates()).isEmpty();
        assertThatThrownBy(() -> requests.create(target, first, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("awaiting committee review");
        assertThatThrownBy(() -> service.release(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("after the signup deadline");
        releaseAfterDeadline(target);
        assertThat(notificationCount(DARIO)).isEqualTo(1);
        assertThat(options(DARIO).candidates()).hasSize(4);

        int before = notificationCount(firstProfessor);
        var request = requests.create(target, first, null);
        assertThat(notificationCount(firstProfessor)).isEqualTo(before + 1);
        var next = requests.create(target, second, null);
        assertThatThrownBy(() -> service.respond(secondProfessor, request.requestId(), true))
                .isInstanceOf(NotFoundException.class);

        service.respond(firstProfessor, request.requestId(), false);
        service.respond(secondProfessor, next.requestId(), true, dateFor(next.requestId()));
        assertThat(observationsOf(target)).hasSize(1);
        assertThatThrownBy(() -> requests.create(target, candidates.get(2).observerId(), null))
                .isInstanceOf(ConflictException.class);
        assertThat(service.facultyState(DARIO).outgoing())
                .extracting(ObserverEligibilityService.Request::status)
                .containsExactlyInAnyOrder("CONFIRMED", "DECLINED");
        assertThat(notificationCount(DARIO)).isEqualTo(3);
    }

    @Test
    void releaseAndAcceptanceRecheckEligibility() {
        int target = darioSignsUp();
        signupDeadline("2026-10-20");
        // Arlo moves to another department after the list was generated.
        int department = departmentOf(ARLO);
        moveToDepartment(ARLO, 2);
        assertThatThrownBy(() -> service.release(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer eligible");
        moveToDepartment(ARLO, department);
        service.release(target);
        var request = requests.create(target, observerOf(db, ARLO), null);
        moveToDepartment(ARLO, 2);
        assertThatThrownBy(() -> service.respond(ARLO, request.requestId(), true, LocalDate.of(2026, 11, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer eligible");
    }

    @Test
    void acceptanceRequiresAnAvailableClassDateAndSavesTheClassTime() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        var candidate = list(target).candidates().getFirst();
        var request = requests.create(target, candidate.observerId(), null);
        int professor = candidate.professorId();
        assertThat(service.availableDates(professor, request.requestId()))
                .containsExactly(
                        LocalDate.of(2026, 11, 3),
                        LocalDate.of(2026, 11, 5),
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 12));
        // Nov 1 is a Sunday, when Dario's TR class does not meet.
        assertThatThrownBy(() -> service.respond(professor, request.requestId(), true, LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        service.respond(professor, request.requestId(), true, LocalDate.of(2026, 11, 3));
        assertThat(only(observationsOf(target)).status()).isEqualTo("SCHEDULED");
        assertThat(only(observationsOf(target)).startTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void aLiveInvitationAwaitsAResponseAndAnAcceptedObserverMatches() {
        int target = darioSignsUp();
        assertThat(matching(target).status()).isEqualTo("AWAITING_INVITATION");
        invitation(target, ARLO, "PENDING", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("AWAITING_RESPONSE");
        invitation(target, KEIRA, "CONFIRMED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("MATCHED");
    }

    @Test
    void allRecommendedObserversMustDeclineOrExpireBeforeTheCaseIsUnmatched() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        invitation(target, LEANDER, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        // This invitation is still stored as pending but has passed its expiration.
        invitation(target, ARLO, "PENDING", LocalDateTime.parse("2026-10-20T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("AWAITING_INVITATION");
        invitation(target, AVERY, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        invitation(target, KEIRA, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("UNMATCHED");
        assertThat(matching(target).reason()).isEqualTo("INVITATIONS_UNSUCCESSFUL");
    }

    @Test
    void replacementBatchesNeedAnExhaustedListAndNeverReuseRecommendations() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        addUnusedObservers(7);
        // Uninvited recommendations remain, so there is nothing to replace yet.
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        var original = candidateIds(target);
        for (int professor : candidateProfessors(target))
            invitation(target, professor, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        int notifications = notificationCount(DARIO);

        service.regenerate(target);
        assertThat(candidateIds(target)).hasSize(5).doesNotContainAnyElementsOf(original);
        assertThat(matching(target).previousRecommendations()).hasSize(original.size());
        assertThat(matching(target).reason()).isEqualTo("RECOMMENDATIONS_AWAITING_RELEASE");
        assertThat(options(DARIO).candidates()).isEmpty();

        releaseAfterDeadline(target);
        assertThat(matching(target).status()).isEqualTo("AWAITING_INVITATION");
        assertThat(notificationCount(DARIO)).isEqualTo(notifications + 1);
        assertThat(options(DARIO).candidates()).hasSize(5);
    }

    @Test
    void manualAssignmentRequiresAllDeclinesAndExhaustionOfTheWholePool() {
        int target = darioSignsUp();
        int observer = candidateIds(target).getFirst();
        assertThatThrownBy(() -> service.assign(target, observer)).isInstanceOf(ConflictException.class);
        exhaustForAssignment(target);
        assertThat(matching(target).canAssign()).isTrue();
        // A newly eligible observer has to be recommended before the committee assigns someone by hand.
        signupDeadline("2026-10-31");
        addUnusedObservers(1);
        signupDeadline("2026-10-20");
        assertThat(matching(target).canAssign()).isFalse();
        assertThatThrownBy(() -> service.assign(target, observer)).isInstanceOf(ConflictException.class);
    }

    @Test
    void assignedObserverChoosesTheObservationDate() {
        int target = darioSignsUp();
        var candidate = list(target).candidates().getFirst();
        exhaustForAssignment(target);
        service.assign(target, candidate.observerId());
        var request = service.facultyState(candidate.professorId()).incoming().getFirst();
        assertThat(request.assignedByAc()).isTrue();
        assertThat(request.observationDate()).isNull();
        service.scheduleObservation(candidate.professorId(), request.requestId(), LocalDate.of(2026, 11, 3));
        assertThat(service.facultyState(candidate.professorId())
                        .incoming()
                        .getFirst()
                        .observationDate())
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void deferralRequiresZeroEligibleObserversAndPreservesTheDecision() {
        int target = darioSignsUp();
        assertThatThrownBy(() -> service.defer(target, 12, "No matching observers"))
                .isInstanceOf(ConflictException.class);
        deactivateObservers();
        signupDeadline("2026-10-20");
        assertThat(matching(target).reason()).isEqualTo("NO_ELIGIBLE_OBSERVERS");
        assertThat(matching(target).canDefer()).isTrue();
        assertThatThrownBy(() -> service.defer(target, 12, " ")).isInstanceOf(IllegalArgumentException.class);
        service.defer(target, 12, "No matching observers");
        var state = matching(target);
        assertThat(state.status()).isEqualTo("DEFERRED");
        assertThat(state.deferral().reason()).isEqualTo("No matching observers");
        assertThat(state.deferral().targetSemester()).isEqualTo("Spring 2027");
        assertThat(service.facultyState(DARIO).notifications().getFirst().message())
                .contains("Due next semester");
        service.generate();
        assertThat(matching(target).status()).isEqualTo("DEFERRED");
    }

    @Test
    void deadlinesUpdateCycleAndDefaultWindows() {
        var updated = service.updateDeadlines(1, deadlines("2026-11-01", "2026-11-20", "2026-12-05"));
        assertThat(updated.signupDeadline()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(updated.observationDeadline()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(updated.feedbackDeadline()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(list(KEIRA_SIGNUP).windowStart()).isEqualTo(LocalDate.parse("2026-11-02"));
        assertThat(list(KEIRA_SIGNUP).windowEnd()).isEqualTo(LocalDate.parse("2026-11-20"));
    }

    @Test
    void deadlinesRejectInvalidOrderAndDatesThatExcludeScheduledObservations() {
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-11-01", "2026-10-31", "2026-12-01")))
                .isInstanceOf(IllegalArgumentException.class);
        // Leander's observation is scheduled for Nov 4.
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-11-04", "2026-11-15", "2026-12-01")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existing observation date");
        assertThat(service.cycle().observationDeadline()).isEqualTo(LocalDate.parse("2026-11-15"));
    }

    @Test
    void deadlinesCanOnlyBeExtendedBeforeOrOnTheirDeadline() {
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-10-30", "2026-11-15", "2026-12-01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only be extended");
        db.update(withSignupDeadline(db.findById(1, EvaluationCycle.class), LocalDate.parse("2026-10-20")));
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-10-31", "2026-11-15", "2026-12-01")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("has passed");
        var updated = service.updateDeadlines(1, deadlines("2026-10-20", "2026-11-20", "2026-12-01"));
        assertThat(updated.observationDeadline()).isEqualTo(LocalDate.parse("2026-11-20"));
        db.update(withSignupDeadline(db.findById(1, EvaluationCycle.class), LocalDate.parse("2026-10-21")));
        assertThat(service.updateDeadlines(1, deadlines("2026-10-22", "2026-11-20", "2026-12-01"))
                .signupDeadline()).isEqualTo(LocalDate.parse("2026-10-22"));
    }

    int signup(int professorId, int sectionId) {
        return signups.save(professorId, FALL_2026, sectionId, null).signupId();
    }

    int darioSignsUp() {
        return signup(DARIO, 298);
    }

    ObserverEligibilityService.Signup list(int signupId) {
        return service.acState().signups().stream()
                .filter(s -> s.signupId() == signupId)
                .findFirst()
                .orElseThrow();
    }

    List<Integer> candidateIds(int signupId) {
        return list(signupId).candidates().stream()
                .map(ObserverEligibilityService.Candidate::observerId)
                .toList();
    }

    List<Integer> candidateProfessors(int signupId) {
        return list(signupId).candidates().stream()
                .map(ObserverEligibilityService.Candidate::professorId)
                .toList();
    }

    List<Integer> eligibleProfessors(int signupId) {
        return service.eligibleObservers(signupId).stream()
                .map(ObserverEligibilityService.Candidate::professorId)
                .toList();
    }

    ObserverRequestService.RequestOptions options(int professorId) {
        return requests.options(professorId).getFirst();
    }

    int notificationCount(int professorId) {
        return service.facultyState(professorId).notifications().size();
    }

    ObserverEligibilityService.MatchingCase matching(int signupId) {
        return service.acState().matchingCases().stream()
                .filter(c -> c.signupId() == signupId)
                .findFirst()
                .orElseThrow();
    }

    int departmentOf(int professorId) {
        return db.findById(professorId, Professor.class).departmentId();
    }

    void invitation(int signupId, int professorId, String status, LocalDateTime expires) {
        db.insert(new ObserverRequest(
                null,
                signupId,
                observerOf(db, professorId),
                LocalDateTime.parse("2026-10-20T10:00:00"),
                expires,
                null,
                status,
                false));
    }

    void signupDeadline(String date) {
        db.update(withSignupDeadline(db.findById(1, EvaluationCycle.class), LocalDate.parse(date)));
    }

    void deactivateObservers() {
        observers.findByCycleId(1).forEach(o -> db.update(inactive(o)));
    }

    void moveToDepartment(int professorId, int departmentId) {
        db.update(withDepartment(db.findById(professorId, Professor.class), departmentId));
    }

    void releasedAt(int signupId, LocalDateTime releasedAt) {
        db.update(withReleasedAt(db.findById(signupId, ObserverListState.class), releasedAt));
    }

    List<Observation> observationsOf(int signupId) {
        return observations.findBySignupIdIn(List.of(signupId));
    }

    void releaseAfterDeadline(int signupId) {
        signupDeadline("2026-10-20");
        service.release(signupId);
        signupDeadline("2026-10-31");
    }

    void exhaustForAssignment(int target) {
        signupDeadline("2026-10-20");
        releasedAt(target, LocalDateTime.parse("2026-10-21T10:00:00"));
        for (int professor : candidateProfessors(target))
            invitation(target, professor, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
    }

    void addUnusedObservers(int count) {
        for (int i = 0; i < count; i++) {
            String email = "replacement-" + i + "@test.edu";
            int professor = db.insert(new Professor(
                            null,
                            db.findById(DARIO, Professor.class).departmentId(),
                            "FULL",
                            "Fresh",
                            "Observer" + i,
                            email,
                            LocalDate.parse("2020-01-01"),
                            true))
                    .professorId();
            db.insert(new TeachingHistory(null, professor, 300));
            signup(professor, 300);
        }
    }

    void historicalCourse(int professorId, LocalDate start, LocalDate end) {
        int semester = db.insert(new Semester(null, "History test", start.getYear(), start, end))
                .semesterId();
        int course = db.findById(298, CourseSection.class).courseId();
        int section = db.insert(new CourseSection(
                        null, course, semester, "HIST", "TR", LocalTime.of(13, 0), LocalTime.of(14, 15), null))
                .sectionId();
        db.insert(new TeachingHistory(null, professorId, section));
    }

    void clearPriorTeaching(int professorId) {
        teaching.deleteAll(teaching.findByProfessorId(professorId).stream()
                .filter(t -> db.findById(t.sectionId(), CourseSection.class).semesterId() != FALL_2026)
                .toList());
    }

    static ObserverEligibilityService.Deadlines deadlines(String signup, String observation, String feedback) {
        return new ObserverEligibilityService.Deadlines(
                LocalDate.parse(signup), LocalDate.parse(observation), LocalDate.parse(feedback));
    }

    private LocalDate dateFor(int requestId) {
        int observer = db.findById(requestId, ObserverRequest.class).observerId();
        int professor = db.findById(observer, Observer.class).professorId();
        return service.availableDates(professor, requestId).getFirst();
    }
}
