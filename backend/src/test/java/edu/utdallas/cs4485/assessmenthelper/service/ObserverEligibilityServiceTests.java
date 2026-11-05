package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;

import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
    ObserverRequestContextRepository.class,
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
    static final int LEANDER = 13, ARLO = 26, AVERY = 31, KEIRA = 41, XIOMARA = 54, PERRIN = 17;
    static final int KEIRA_SIGNUP = 32;

    @Autowired
    ObserverEligibilityService service;

    @Autowired
    EvaluationSignupService signups;

    @Autowired
    ObserverRequestService requests;

    @Autowired
    JdbcTemplate db;

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

    void invitation(int signupId, int professorId, String status, LocalDateTime expires) {
        int observerId = db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1", Integer.class, professorId);
        db.update(
                "INSERT INTO observer_request(signup_id,observer_id,request_date,expiration_date,status) VALUES (?,?,?, ?,?)",
                signupId,
                observerId,
                LocalDateTime.parse("2026-10-20T10:00:00"),
                expires,
                status);
    }

    @Test
    void matchingUsesTheFullEligiblePoolInsteadOfTheStoredRecommendationCount() {
        int target = darioSignsUp();
        db.update("DELETE FROM observer_match WHERE signup_id=?", target);
        var state = matching(target);
        assertThat(state.status()).isEqualTo("AWAITING_INVITATION");
        assertThat(state.reason()).isNull();
        assertThat(state.eligibleObserverCount()).isEqualTo(4);
        db.update("UPDATE observer SET active_status=FALSE WHERE cycle_id=1");
        assertThat(matching(target).reason()).isEqualTo("NO_ELIGIBLE_OBSERVERS");
    }

    @Test
    void sixEligibleObserversLeaveOneUnusedForKeirasReplacementList() {
        int rowan = db.queryForObject(
                "SELECT professor_id FROM professor WHERE email='rowan.cedarvale@example.edu'", Integer.class);
        var eligible = service.eligibleObservers(KEIRA_SIGNUP).stream()
                .map(ObserverEligibilityService.Candidate::professorId)
                .toList();
        assertThat(eligible).hasSize(6).contains(rowan);
        service.generate();
        var first = candidateProfessors(KEIRA_SIGNUP);
        assertThat(first).hasSize(5).isSubsetOf(eligible);
        int remaining =
                eligible.stream().filter(id -> !first.contains(id)).findFirst().orElseThrow();
        releaseAfterDeadline(KEIRA_SIGNUP);
        for (int professor : candidateProfessors(KEIRA_SIGNUP))
            invitation(KEIRA_SIGNUP, professor, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(KEIRA_SIGNUP).unusedEligibleObserverCount()).isEqualTo(1);
        service.regenerate(KEIRA_SIGNUP);
        assertThat(candidateProfessors(KEIRA_SIGNUP)).containsExactly(remaining);
        releaseAfterDeadline(KEIRA_SIGNUP);
        var request = requests.create(KEIRA_SIGNUP, candidateIds(KEIRA_SIGNUP).getFirst(), null);
        requests.confirm(request.requestId(), dateFor(request.requestId()));
        assertThat(matching(KEIRA_SIGNUP).status()).isEqualTo("MATCHED");
    }

    @Test
    void staleRecommendationsDoNotHideTheAbsenceOfEligibleObservers() {
        int target = darioSignsUp();
        db.update("UPDATE observer SET active_status=FALSE WHERE cycle_id=1");
        assertThat(list(target).candidates()).isNotEmpty();
        assertThat(matching(target).status()).isEqualTo("UNMATCHED");
        assertThat(matching(target).reason()).isEqualTo("NO_ELIGIBLE_OBSERVERS");
    }

    @Test
    void allRecommendedObserversMustDeclineOrExpireBeforeTheCaseIsUnmatched() {
        int target = darioSignsUp();
        invitation(target, LEANDER, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        // At exactly the expiration time this still-persisted pending invitation is effectively expired.
        invitation(target, ARLO, "PENDING", LocalDateTime.parse("2026-10-21T10:00:00"));
        var state = matching(target);
        assertThat(state.status()).isEqualTo("AWAITING_INVITATION");
        assertThat(state.reason()).isNull();
        assertThat(state.remainingObserverCount()).isEqualTo(2);
        assertThat(state.invitations())
                .extracting(ObserverEligibilityService.Invitation::status)
                .containsExactlyInAnyOrder("DECLINED", "EXPIRED");
        assertThat(state.invitations())
                .extracting(ObserverEligibilityService.Invitation::observerName)
                .contains("Arlo Glimmerton");
        releaseAfterDeadline(target);
        signup(HOLLIS, 284);
        invitation(target, AVERY, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        invitation(target, KEIRA, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("UNMATCHED");
        assertThat(matching(target).reason()).isEqualTo("INVITATIONS_UNSUCCESSFUL");
        assertThat(matching(target).remainingObserverCount()).isEqualTo(1);
    }

    @Test
    void repeatedHistoricalDeclinesFromOneObserverCannotExhaustTheRecommendationList() {
        int target = darioSignsUp();
        for (int i = 0; i < 5; i++) invitation(target, LEANDER, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("AWAITING_INVITATION");
        assertThat(matching(target).reason()).isNull();
        assertThat(matching(target).remainingObserverCount()).isEqualTo(3);
    }

    @Test
    void aLiveInvitationIsNotUnmatchedAndAnAcceptedObserverTakesPrecedence() {
        int target = darioSignsUp();
        invitation(target, LEANDER, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        invitation(target, ARLO, "PENDING", LocalDateTime.parse("2026-10-21T10:00:01"));
        assertThat(matching(target).status()).isEqualTo("AWAITING_RESPONSE");
        db.update("UPDATE observer SET active_status=FALSE WHERE cycle_id=1");
        assertThat(matching(target).status()).isEqualTo("AWAITING_RESPONSE");
        invitation(target, KEIRA, "CONFIRMED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).status()).isEqualTo("MATCHED");
        assertThat(matching(target).reason()).isNull();
    }

    @Test
    void manualAssignmentAndMultipleAcceptedObserversDoNotInflateTheUnmatchedList() {
        int target = darioSignsUp();
        int observerId = candidateIds(target).getFirst();
        exhaustForAssignment(target);
        service.assign(target, observerId);
        assertThat(matching(target).status()).isEqualTo("MATCHED");
        assertThat(matching(target).invitations())
                .extracting(ObserverEligibilityService.Invitation::status)
                .contains("ASSIGNED");
        int another = candidateIds(target).stream()
                .filter(id -> id != observerId)
                .findFirst()
                .orElseThrow();
        db.update(
                "INSERT INTO observation(signup_id,observer_id,template_id,status) VALUES (?,?,(SELECT MIN(template_id) FROM observation_template),'ACCEPTED')",
                target,
                another);
        assertThat(service.acState().signups().stream().filter(s -> s.signupId() == target))
                .hasSize(1);
        assertThat(service.acState().matchingCases().stream().filter(s -> s.signupId() == target))
                .hasSize(1);
    }

    void releaseAfterDeadline(int signupId) {
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        service.release(signupId);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-31' WHERE cycle_id=1");
    }

    int exhaustedReleasedList() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        for (int professor : candidateProfessors(target))
            invitation(target, professor, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        return target;
    }

    void addUnusedObservers(int count) {
        for (int i = 0; i < count; i++) {
            String email = "replacement-" + i + "@test.edu";
            db.update("""
                    INSERT INTO professor(department_id,professor_rank,first_name,last_name,email,hire_date,active_status)
                    SELECT department_id,'FULL','Fresh',?,?,'2020-01-01',TRUE FROM professor WHERE professor_id=?
                    """, "Observer" + i, email, DARIO);
            int professor = db.queryForObject("SELECT professor_id FROM professor WHERE email=?", Integer.class, email);
            db.update("INSERT INTO teaching_history(professor_id,section_id) VALUES (?,300)", professor);
            signup(professor, 300);
        }
    }

    @Test
    void replacementBatchesAreCappedReviewedReleasedAndNeverReusePreviousRecommendations() {
        int target = exhaustedReleasedList();
        var original = candidateIds(target);
        int originalMatch =
                db.queryForObject("SELECT MIN(match_id) FROM observer_match WHERE signup_id=?", Integer.class, target);
        int notifications = notificationCount(DARIO);
        addUnusedObservers(7);
        service.regenerate(target);
        var firstBatch = candidateIds(target);
        assertThat(firstBatch).hasSize(5).doesNotContainAnyElementsOf(original);
        assertThat(matching(target).previousRecommendations()).hasSize(original.size());
        assertThat(matching(target).invitations()).hasSize(original.size());
        assertThat(matching(target).reason()).isEqualTo("RECOMMENDATIONS_AWAITING_RELEASE");
        assertThat(matching(target).status()).isEqualTo("UNMATCHED");
        assertThat(list(target).released()).isFalse();
        assertThat(service.facultyState(DARIO).signup().candidates()).isEmpty();
        assertThat(options(DARIO).candidates()).isEmpty();
        assertThat(notificationCount(DARIO)).isEqualTo(notifications);
        assertThatThrownBy(() -> service.regenerate(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Review and send");
        assertThat(candidateIds(target)).containsExactlyElementsOf(firstBatch);
        releaseAfterDeadline(target);
        assertThat(matching(target).status()).isEqualTo("AWAITING_INVITATION");
        assertThat(notificationCount(DARIO)).isEqualTo(notifications + 1);
        assertThat(service.facultyState(DARIO).notifications().getFirst().message())
                .contains("new set of observer recommendations");
        assertThat(options(DARIO).candidates()).hasSize(5);
        assertThatThrownBy(() -> requests.createFromMatch(DARIO, target, originalMatch, null))
                .isInstanceOf(ConflictException.class);
        releaseAfterDeadline(target);
        assertThat(notificationCount(DARIO)).isEqualTo(notifications + 1);
        for (int observer : firstBatch)
            requests.decline(requests.create(target, observer, null).requestId());
        service.regenerate(target);
        var lastBatch = candidateIds(target);
        assertThat(lastBatch).hasSize(2).doesNotContainAnyElementsOf(firstBatch).doesNotContainAnyElementsOf(original);
        assertThat(matching(target).previousRecommendations()).hasSize(original.size() + firstBatch.size());
        service.generate();
        assertThat(candidateIds(target)).containsExactlyElementsOf(lastBatch);
        releaseAfterDeadline(target);
        for (int observer : lastBatch)
            requests.decline(requests.create(target, observer, null).requestId());
        assertThatThrownBy(() -> service.regenerate(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No additional eligible observers");
        assertThat(candidateIds(target)).containsExactlyElementsOf(lastBatch);
        assertThat(list(target).released()).isTrue();
        assertThat(matching(target).invitations()).hasSize(original.size() + firstBatch.size() + lastBatch.size());
        assertThat(matching(target).status()).isEqualTo("UNMATCHED");
    }

    @Test
    void regenerationWithNoUnusedObserversKeepsTheExistingListAndHistory() {
        int target = exhaustedReleasedList();
        var original = candidateIds(target);
        assertThat(matching(target).unusedEligibleObserverCount()).isZero();
        assertThatThrownBy(() -> service.regenerate(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No additional eligible observers");
        assertThat(candidateIds(target)).containsExactlyElementsOf(original);
        assertThat(matching(target).previousRecommendations()).isEmpty();
        assertThat(list(target).released()).isTrue();
    }

    @Test
    void regenerationRejectsUninvitedCandidatesAndLiveRequests() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        addUnusedObservers(1);
        var original = candidateIds(target);
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        invitation(target, LEANDER, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        invitation(target, ARLO, "PENDING", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        assertThat(candidateIds(target)).containsExactlyElementsOf(original);
    }

    @Test
    void regenerationRejectsAMatchedSignupAndAClosedObservationPeriod() {
        int target = exhaustedReleasedList();
        int observer = candidateIds(target).getFirst();
        exhaustForAssignment(target);
        service.assign(target, observer);
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        db.update("UPDATE evaluation_cycle SET observation_deadline='2026-10-20' WHERE cycle_id=1");
        assertThatThrownBy(() -> service.regenerate(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
        assertThat(matching(target).previousRecommendations()).isEmpty();
    }

    @Test
    void replacementReleaseRechecksEligibilityBeforeNotifyingTheObservee() {
        int target = exhaustedReleasedList();
        addUnusedObservers(1);
        int notifications = notificationCount(DARIO);
        service.regenerate(target);
        int replacement = candidateIds(target).getFirst();
        db.update("UPDATE observer SET active_status=FALSE WHERE observer_id=?", replacement);
        assertThatThrownBy(() -> releaseAfterDeadline(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer eligible");
        assertThat(list(target).released()).isFalse();
        assertThat(notificationCount(DARIO)).isEqualTo(notifications);
    }

    @Test
    void releaseWaitsUntilTheDayAfterSignupDeadline() {
        int target = darioSignsUp();
        assertThat(service.cycle().candidateListsOpen()).isFalse();
        assertThatThrownBy(() -> service.release(target))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("after the signup deadline");
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-21' WHERE cycle_id=1");
        assertThat(service.cycle().candidateListsOpen()).isFalse();
        assertThatThrownBy(() -> service.release(target)).isInstanceOf(ConflictException.class);
        assertThat(list(target).released()).isFalse();
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThat(service.cycle().candidateListsOpen()).isTrue();
        service.release(target);
        assertThat(list(target).released()).isTrue();
    }

    void historicalCourse(int professorId, LocalDate start, LocalDate end) {
        db.update(
                "INSERT INTO semester (term, academic_year, start_date, end_date) VALUES ('History test', ?, ?, ?)",
                start.getYear(),
                start,
                end);
        int semester = db.queryForObject("SELECT MAX(semester_id) FROM semester", Integer.class);
        int course = db.queryForObject("SELECT course_id FROM course_section WHERE section_id=298", Integer.class);
        db.update(
                "INSERT INTO course_section (course_id, semester_id, section_number, meeting_days, start_time, end_time) VALUES (?, ?, 'HIST', 'TR', '13:00:00', '14:15:00')",
                course,
                semester);
        int section = db.queryForObject("SELECT MAX(section_id) FROM course_section", Integer.class);
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (?, ?)", professorId, section);
    }

    void clearPriorTeaching(int professorId) {
        db.update(
                "DELETE FROM teaching_history WHERE professor_id=? AND section_id IN (SELECT section_id FROM course_section WHERE semester_id<>?)",
                professorId,
                FALL_2026);
    }

    @Test
    void teachingAtTwoYearBoundaryQualifiesWithoutUsingHistoricalTimesForAvailability() {
        clearPriorTeaching(HOLLIS);
        LocalDate cutoff = service.cycle().startDate().minusYears(2);
        historicalCourse(HOLLIS, cutoff.minusMonths(4), cutoff);
        signup(HOLLIS, 284);
        int target = darioSignsUp();
        assertThat(candidateProfessors(target)).contains(HOLLIS);
        assertThat(list(target).candidates().stream()
                        .filter(c -> c.professorId() == HOLLIS)
                        .findFirst()
                        .orElseThrow()
                        .qualifyingCourses())
                .anyMatch(course -> course.contains("HIST"));
    }

    @Test
    void teachingOlderThanTwoYearsAndFutureTeachingDoNotQualify() {
        clearPriorTeaching(HOLLIS);
        LocalDate cutoff = service.cycle().startDate().minusYears(2);
        historicalCourse(HOLLIS, cutoff.minusMonths(4), cutoff.minusDays(1));
        historicalCourse(
                HOLLIS,
                service.cycle().startDate().plusMonths(6),
                service.cycle().startDate().plusMonths(9));
        signup(HOLLIS, 284);
        assertThat(candidateProfessors(darioSignsUp())).doesNotContain(HOLLIS);
    }

    @Test
    void deadlinesUpdateCycleAndDefaultWindows() {
        var updated = service.updateDeadlines(
                1,
                new ObserverEligibilityService.Deadlines(
                        LocalDate.parse("2026-10-30"), LocalDate.parse("2026-11-20"), LocalDate.parse("2026-12-05")));
        assertThat(updated.signupDeadline()).isEqualTo(LocalDate.parse("2026-10-30"));
        assertThat(updated.observationDeadline()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(updated.feedbackDeadline()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(list(KEIRA_SIGNUP).windowStart()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(list(KEIRA_SIGNUP).windowEnd()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(service.facultyState(LEANDER).cycle().feedbackDeadline()).isEqualTo(updated.feedbackDeadline());
    }

    @Test
    void deadlinesRejectInvalidOrderAndDatesThatExcludeScheduledObservations() {
        assertThatThrownBy(() -> service.updateDeadlines(
                        1,
                        new ObserverEligibilityService.Deadlines(
                                LocalDate.parse("2026-11-01"),
                                LocalDate.parse("2026-10-31"),
                                LocalDate.parse("2026-12-01"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateDeadlines(
                        1,
                        new ObserverEligibilityService.Deadlines(
                                LocalDate.parse("2026-10-31"),
                                LocalDate.parse("2026-11-02"),
                                LocalDate.parse("2026-12-01"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existing observation date");
        assertThat(service.cycle().observationDeadline()).isEqualTo(LocalDate.parse("2026-11-15"));
    }

    @Test
    void listsAreCappedAtFiveStayStableAndReleasedListsStopGrowing() {
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).containsExactlyInAnyOrder(LEANDER, ARLO, AVERY, KEIRA);
        // Keira's list remains capped at five even with the expanded pool.
        assertThat(candidateProfessors(KEIRA_SIGNUP))
                .hasSize(5)
                .isSubsetOf(service.eligibleObservers(KEIRA_SIGNUP).stream()
                        .map(ObserverEligibilityService.Candidate::professorId)
                        .toList());
        var darioIds = candidateIds(dario);
        var keiraIds = candidateIds(KEIRA_SIGNUP);
        service.generate();
        assertThat(candidateIds(dario)).containsExactlyElementsOf(darioIds);
        assertThat(candidateIds(KEIRA_SIGNUP)).containsExactlyElementsOf(keiraIds);
        assertThat(service.facultyState(DARIO).signup().candidates()).isEmpty();
        releaseAfterDeadline(dario);
        assertThat(service.facultyState(DARIO).signup().candidates()).hasSize(4);
        // Hollis becomes eligible by teaching a CS 3345 section (TR 17:30), but Dario's list has been sent.
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (?, 300)", HOLLIS);
        signup(HOLLIS, 284);
        assertThat(candidateIds(dario)).containsExactlyElementsOf(darioIds);
    }

    @Test
    void filtersDepartmentLevelActivitySemesterAndTeachingOverlap() {
        clearPriorTeaching(HOLLIS);
        db.update("UPDATE professor SET department_id = 2 WHERE professor_id = ?", ARLO);
        db.update("UPDATE professor SET active_status = FALSE WHERE professor_id = ?", AVERY);
        db.update("UPDATE observer SET active_status = FALSE WHERE professor_id = ? AND cycle_id = 1", KEIRA);
        // Hollis teaches only 2000-level courses; Ansel's CS 3345 section is in Spring 2027.
        signup(HOLLIS, 284);
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (?, 322)", ANSEL);
        signup(ANSEL, 290);
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).containsExactly(LEANDER);
    }

    @Test
    void releaseThenMultipleRequestsWithDeclineAndAccept() {
        int target = darioSignsUp();
        var candidates = list(target).candidates();
        assertThat(candidates).hasSize(4);
        int first = candidates.get(0).observerId(), second = candidates.get(1).observerId();
        int firstProfessor = candidates.get(0).professorId(),
                secondProfessor = candidates.get(1).professorId();

        // Dario sees no candidates and cannot request until the committee releases the list.
        assertThat(options(DARIO).candidates()).isEmpty();
        assertThat(options(DARIO).unavailableReason()).isEqualTo("Your observer list is awaiting committee review.");
        assertThatThrownBy(() -> requests.create(target, first, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("awaiting committee review");
        releaseAfterDeadline(target);
        assertThat(notificationCount(DARIO)).isEqualTo(1);
        assertThat(options(DARIO).candidates()).hasSize(4);
        assertThat(options(DARIO).unavailableReason()).isNull();

        int before = notificationCount(firstProfessor);
        var request = requests.create(target, first, null);
        assertThat(requests.create(target, first, null).requestId()).isEqualTo(request.requestId());
        assertThat(notificationCount(firstProfessor)).isEqualTo(before + 1);
        var next = requests.create(target, second, null);
        assertThat(next.requestId()).isNotEqualTo(request.requestId());
        assertThatThrownBy(() -> service.respond(secondProfessor, request.requestId(), true))
                .isInstanceOf(NotFoundException.class);

        service.respond(firstProfessor, request.requestId(), false);
        assertThat(requests.create(target, second, null).requestId()).isEqualTo(next.requestId());
        service.respond(secondProfessor, next.requestId(), true, dateFor(next.requestId()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isEqualTo(1);
        assertThat(options(DARIO).unavailableReason())
                .isEqualTo("An observation is already scheduled for this signup.");
        assertThatThrownBy(() -> requests.create(target, candidates.get(2).observerId(), null))
                .isInstanceOf(ConflictException.class);
        assertThat(service.facultyState(DARIO).outgoing())
                .extracting(ObserverEligibilityService.Request::status)
                .containsExactlyInAnyOrder("CONFIRMED", "DECLINED");
        assertThat(notificationCount(DARIO)).isEqualTo(3);
    }

    @Test
    void confirmingThroughTheRequestApiRecordsTheObservation() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        var request =
                requests.create(target, list(target).candidates().getFirst().observerId(), null);
        assertThat(requests.confirm(request.requestId(), dateFor(request.requestId()))
                        .status())
                .isEqualTo("CONFIRMED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isEqualTo(1);
        assertThatThrownBy(() -> requests.confirm(request.requestId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsBadWindowsWrongClassMatchedChangesAndDeadlines() {
        assertThat(signups.window(DARIO, FALL_2026))
                .isEqualTo(new ObserverEligibilityService.Window(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), null, LocalDate.of(2026, 10, 31), true));
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 10, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        // Nov 1 is a Sunday, so a one-day window has no TR meeting.
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no class meetings");
        db.update("UPDATE course_section SET meeting_days = NULL WHERE section_id = 284");
        assertThatThrownBy(() -> signup(HOLLIS, 284))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete meeting schedule");

        LocalDate end = LocalDate.of(2026, 11, 12);
        int target = signups.save(DARIO, FALL_2026, 298, end).signupId();
        assertThat(signups.window(DARIO, FALL_2026).end()).isEqualTo(end);
        assertThat(signups.save(DARIO, FALL_2026, 298, null).signupId()).isEqualTo(target);
        assertThatThrownBy(() -> signups.save(HOLLIS, FALL_2026, 298, end))
                .isInstanceOf(IllegalArgumentException.class);
        // Candidates exist, so the selection is locked.
        assertThatThrownBy(() -> signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 11, 10)))
                .isInstanceOf(ConflictException.class);
        releaseAfterDeadline(target);
        int self = db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id = ? AND cycle_id = 1", Integer.class, DARIO);
        assertThatThrownBy(() -> requests.create(target, self, null)).isInstanceOf(ConflictException.class);

        db.update("UPDATE evaluation_cycle SET signup_deadline = '2026-10-20' WHERE cycle_id = 1");
        assertThatThrownBy(() -> signup(HOLLIS, 288)).isInstanceOf(ConflictException.class);
        // Lists can still be released and requests sent after signup closes.
        int candidate = list(target).candidates().getFirst().observerId();
        requests.create(target, candidate, null);
        db.update("DELETE FROM observer_notification WHERE signup_id = ?", target);
        db.update("DELETE FROM observer_request WHERE signup_id = ?", target);
        db.update("UPDATE evaluation_cycle SET observation_deadline = '2026-10-20' WHERE cycle_id = 1");
        assertThatThrownBy(() -> requests.create(target, candidate, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
    }

    @Test
    void scheduleParsingAndBackToBackClasses() {
        assertThat(ObserverEligibilityService.days("Monday / Wednesday"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(ObserverEligibilityService.days("TR"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        assertThat(ObserverEligibilityService.level("3345")).isEqualTo(3000);
        assertThat(ObserverEligibilityService.level("101")).isEqualTo(-1);
        // Perrin's class now ends at 13:00, exactly when Dario's starts, so it no longer blocks him.
        db.update("UPDATE course_section SET start_time = '11:45', end_time = '13:00' WHERE section_id = 299");
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).contains(PERRIN).doesNotContain(XIOMARA);
        assertThat(list(dario).candidates().stream()
                        .filter(c -> c.professorId() == PERRIN)
                        .findFirst()
                        .orElseThrow()
                        .qualifyingCourses())
                .contains("CS 3345.008 - Fall 2026");
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
        assertThatThrownBy(() -> service.respond(professor, request.requestId(), true))
                .isInstanceOf(IllegalArgumentException.class);
        for (LocalDate invalid :
                List.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 17), LocalDate.of(2026, 10, 20)))
            assertThatThrownBy(() -> service.respond(professor, request.requestId(), true, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.findById(request.requestId()).status()).isEqualTo("PENDING");
        service.respond(professor, request.requestId(), true, LocalDate.of(2026, 11, 3));
        assertThat(db.queryForObject("SELECT status FROM observation WHERE signup_id=?", String.class, target))
                .isEqualTo("SCHEDULED");
        assertThat(db.queryForObject("SELECT start_time FROM observation WHERE signup_id=?", LocalTime.class, target))
                .isEqualTo(LocalTime.of(13, 0));
        assertThat(service.facultyState(professor).incoming().getFirst().observationDate())
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void assignedObserverChoosesDateWithoutChangingAssignedHistory() {
        int target = darioSignsUp();
        var candidate = list(target).candidates().getFirst();
        exhaustForAssignment(target);
        service.assign(target, candidate.observerId());
        var request = service.facultyState(candidate.professorId()).incoming().getFirst();
        assertThat(request.assignedByAc()).isTrue();
        assertThat(request.observationDate()).isNull();
        assertThatThrownBy(() -> service.scheduleObservation(DARIO, request.requestId(), LocalDate.of(2026, 11, 3)))
                .isInstanceOf(NotFoundException.class);
        service.scheduleObservation(candidate.professorId(), request.requestId(), LocalDate.of(2026, 11, 3));
        assertThat(matching(target).invitations().getFirst().status()).isEqualTo("ASSIGNED");
        assertThat(service.facultyState(candidate.professorId())
                        .incoming()
                        .getFirst()
                        .observationDate())
                .isEqualTo(LocalDate.of(2026, 11, 3));
        assertThatThrownBy(() -> service.scheduleObservation(
                        candidate.professorId(), request.requestId(), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void dateOptionsExcludeTeachingConflictsAndRespectDeadlineChanges() {
        int target = darioSignsUp();
        var candidate = list(target).candidates().getFirst();
        exhaustForAssignment(target);
        service.assign(target, candidate.observerId());
        var request = service.facultyState(candidate.professorId()).incoming().getFirst();
        db.update("UPDATE evaluation_cycle SET observation_deadline='2026-11-05' WHERE cycle_id=1");
        assertThat(service.availableDates(candidate.professorId(), request.requestId()))
                .containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));
        db.update(
                "UPDATE course_section SET meeting_days='T',start_time='13:30:00',end_time='14:30:00' WHERE section_id IN (SELECT section_id FROM teaching_history WHERE professor_id=?) AND semester_id=13",
                candidate.professorId());
        assertThat(service.availableDates(candidate.professorId(), request.requestId()))
                .containsExactly(LocalDate.of(2026, 11, 5));
        assertThatThrownBy(() -> service.scheduleObservation(
                        candidate.professorId(), request.requestId(), LocalDate.of(2026, 11, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availableDatesExcludeAnotherObservationAtTheSameTime() {
        int target = darioSignsUp();
        var candidate = list(target).candidates().getFirst();
        exhaustForAssignment(target);
        service.assign(target, candidate.observerId());
        var request = service.facultyState(candidate.professorId()).incoming().getFirst();
        int otherSignup = db.queryForObject(
                "SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.professor_id=? AND oe.cycle_id=1",
                Integer.class,
                XIOMARA);
        db.update("UPDATE evaluation_signup SET section_id=298 WHERE signup_id=?", otherSignup);
        db.update(
                "INSERT INTO observation(signup_id,observer_id,template_id,status,observation_date,start_time) VALUES (?,?,(SELECT template_id FROM observation_template WHERE active_status=TRUE FETCH FIRST 1 ROW ONLY),'SCHEDULED','2026-11-03','13:00:00')",
                otherSignup,
                candidate.observerId());
        assertThat(service.availableDates(candidate.professorId(), request.requestId()))
                .doesNotContain(LocalDate.of(2026, 11, 3))
                .contains(LocalDate.of(2026, 11, 5));
        assertThatThrownBy(() -> service.scheduleObservation(
                        candidate.professorId(), request.requestId(), LocalDate.of(2026, 11, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualAssignmentRequiresAllDeclinesAndExhaustionOfTheWholePool() {
        int target = darioSignsUp();
        int observer = candidateIds(target).getFirst();
        assertThat(matching(target).canAssign()).isFalse();
        assertThatThrownBy(() -> service.assign(target, observer)).isInstanceOf(ConflictException.class);
        exhaustForAssignment(target);
        assertThat(matching(target).canAssign()).isTrue();
        assertThat(matching(target).canDefer()).isFalse();
        assertThatThrownBy(() -> service.defer(target, 12, "No matching observers"))
                .isInstanceOf(ConflictException.class);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-31' WHERE cycle_id=1");
        addUnusedObservers(1);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThat(matching(target).canAssign()).isFalse();
        assertThatThrownBy(() -> service.assign(target, observer)).isInstanceOf(ConflictException.class);
        service.regenerate(target);
        assertThat(matching(target).canAssign()).isFalse();
    }

    @Test
    void expiredInvitationsAllowAssignmentButUninvitedRecommendationsDoNot() {
        int target = darioSignsUp();
        int observer = candidateIds(target).getFirst();
        exhaustForAssignment(target);
        int request = db.queryForObject(
                "SELECT MIN(request_id) FROM observer_request WHERE signup_id=?", Integer.class, target);
        db.update("UPDATE observer_request SET status='EXPIRED' WHERE request_id=?", request);
        assertThat(matching(target).canAssign()).isTrue();
        db.update("DELETE FROM observer_request WHERE request_id=?", request);
        assertThat(matching(target).canAssign()).isFalse();
        assertThatThrownBy(() -> service.assign(target, observer)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deferralRequiresZeroEligibleObserversAndPreservesTheDecision() {
        int target = darioSignsUp();
        assertThatThrownBy(() -> service.defer(target, 12, "No matching observers"))
                .isInstanceOf(ConflictException.class);
        db.update("UPDATE observer SET active_status=FALSE WHERE cycle_id=1");
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThat(matching(target).canDefer()).isTrue();
        assertThat(matching(target).canAssign()).isFalse();
        assertThatThrownBy(() -> service.defer(target, 12, " ")).isInstanceOf(IllegalArgumentException.class);
        service.defer(target, 12, "  No matching observers  ");
        var state = matching(target);
        assertThat(state.status()).isEqualTo("DEFERRED");
        assertThat(state.canAssign()).isFalse();
        assertThat(state.canDefer()).isFalse();
        assertThat(state.deferral().reason()).isEqualTo("No matching observers");
        assertThat(state.deferral().targetSemester()).isEqualTo("Spring 2027");
        assertThat(state.deferral().deferredBy()).isEqualTo("Kaia Clearbrook");
        assertThat(state.deferral().deferredAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 0));
        assertThat(service.facultyState(DARIO).notifications().getFirst().message())
                .contains("Due next semester");
        assertThatThrownBy(() -> service.defer(target, 12, "Again")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.release(target)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.regenerate(target)).isInstanceOf(ConflictException.class);
        service.generate();
        assertThat(matching(target).status()).isEqualTo("DEFERRED");
    }

    @Test
    void demoBiancaCanBeDeferredWithoutChangingExistingObservationDates() {
        int signup = db.queryForObject(
                "SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id WHERE oe.professor_id=27 AND oe.cycle_id=1",
                Integer.class);
        assertThat(matching(signup).eligibleObserverCount()).isZero();
        assertThat(matching(signup).reason()).isEqualTo("NO_ELIGIBLE_OBSERVERS");
        assertThat(service.eligibleObservers(signup)).isEmpty();
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThat(matching(signup).canDefer()).isTrue();
        assertThat(matching(signup).canAssign()).isFalse();
        int before = db.queryForObject("SELECT COUNT(*) FROM observation", Integer.class);
        service.defer(signup, 12, "No matching observers");
        assertThat(matching(signup).status()).isEqualTo("DEFERRED");
        assertThat(matching(signup).deferral().targetSemester()).isEqualTo("Spring 2027");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void expiredEarlierBatchesDoNotBlockAssignmentAfterTheFinalBatchIsExhausted() {
        int target = exhaustedReleasedList();
        int original = candidateIds(target).getFirst();
        db.update("UPDATE observer_request SET status='EXPIRED' WHERE signup_id=? AND observer_id=?", target, original);
        addUnusedObservers(1);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        service.regenerate(target);
        service.release(target);
        int replacement = candidateProfessors(target).getFirst();
        invitation(target, replacement, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        assertThat(matching(target).canAssign()).isTrue();
        assertThat(matching(target).canDefer()).isFalse();
        service.assign(target, original);
        assertThat(matching(target).status()).isEqualTo("MATCHED");
    }

    void exhaustForAssignment(int target) {
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        db.update("UPDATE observer_list_state SET released_at='2026-10-21T10:00:00' WHERE signup_id=?", target);
        for (int professor : candidateProfessors(target)) {
            int observer = db.queryForObject(
                    "SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1", Integer.class, professor);
            if (db.queryForObject(
                            "SELECT COUNT(*) FROM observer_request WHERE signup_id=? AND observer_id=?",
                            Integer.class,
                            target,
                            observer)
                    == 0) invitation(target, professor, "DECLINED", LocalDateTime.parse("2026-10-22T10:00:00"));
        }
    }

    private LocalDate dateFor(int requestId) {
        int professor = db.queryForObject(
                "SELECT ob.professor_id FROM observer_request r JOIN observer ob ON ob.observer_id=r.observer_id WHERE request_id=?",
                Integer.class,
                requestId);
        return service.availableDates(professor, requestId).getFirst();
    }
}
