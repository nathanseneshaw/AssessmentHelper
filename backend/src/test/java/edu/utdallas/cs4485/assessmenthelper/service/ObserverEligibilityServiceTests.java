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

    void releaseAfterDeadline(int signupId) {
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        service.release(signupId);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-31' WHERE cycle_id=1");
    }

    int signupOf(int professorId) {
        return db.queryForObject("""
                SELECT es.signup_id FROM evaluation_signup es JOIN observee oe ON oe.observee_id=es.observee_id
                WHERE oe.professor_id=? AND oe.cycle_id=1
                """, Integer.class, professorId);
    }

    int observerOf(int professorId) {
        return db.queryForObject(
                "SELECT observer_id FROM observer WHERE professor_id=? AND cycle_id=1", Integer.class, professorId);
    }

    int departmentOf(int professorId) {
        return db.queryForObject(
                "SELECT department_id FROM professor WHERE professor_id=?", Integer.class, professorId);
    }

    void observationDeadline(String date) {
        db.update("UPDATE evaluation_cycle SET observation_deadline=? WHERE cycle_id=1", LocalDate.parse(date));
    }

    static ObserverEligibilityService.Deadlines deadlines(String signup, String observation, String feedback) {
        return new ObserverEligibilityService.Deadlines(
                LocalDate.parse(signup), LocalDate.parse(observation), LocalDate.parse(feedback));
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
    void deadlinesRequireAllThreeDatesForTheCurrentCycleOnly() {
        assertThatThrownBy(() -> service.updateDeadlines(
                        1,
                        new ObserverEligibilityService.Deadlines(
                                null, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-12-05"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All three deadlines");
        // Fall 2026 starts on Aug 24.
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-08-23", "2026-11-20", "2026-12-05")))
                .isInstanceOf(IllegalArgumentException.class);
        int fall2025 = db.queryForObject("""
                SELECT c.cycle_id FROM evaluation_cycle c JOIN semester s ON s.semester_id=c.semester_id
                WHERE s.term='Fall' AND s.academic_year=2025
                """, Integer.class);
        assertThatThrownBy(() -> service.updateDeadlines(fall2025, deadlines("2025-10-31", "2025-11-15", "2025-12-01")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("current evaluation cycle");
    }

    @Test
    void shorterDeadlinesTrimWindowsThatWouldRunPastThem() {
        int dario =
                signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 11, 12)).signupId();
        service.updateDeadlines(1, deadlines("2026-10-30", "2026-11-10", "2026-12-05"));
        assertThat(list(dario).windowEnd()).isEqualTo(LocalDate.parse("2026-11-10"));
    }

    @Test
    void deadlinesCannotLeaveASignupWithoutAnObservationWindow() {
        // Dario can be observed only through Tuesday, Nov 3. Leander's observation on Nov 4 still fits these dates.
        signups.save(DARIO, FALL_2026, 298, LocalDate.of(2026, 11, 3));
        assertThatThrownBy(() -> service.updateDeadlines(1, deadlines("2026-11-03", "2026-11-20", "2026-12-05")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("without an observation window");
        assertThat(service.cycle().signupDeadline()).isEqualTo(LocalDate.parse("2026-10-31"));
    }

    @Test
    void releaseSkipsASentListAndRefusesStaleClosedOrAssignedOnes() {
        // Leander's list went out in data.sql, so sending it again changes nothing.
        int before = notificationCount(LEANDER);
        service.release(signupOf(LEANDER));
        assertThat(notificationCount(LEANDER)).isEqualTo(before);

        int dario = darioSignsUp();
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        // Arlo moves to another department after the list was generated.
        int department = departmentOf(ARLO);
        db.update("UPDATE professor SET department_id = 2 WHERE professor_id = ?", ARLO);
        assertThatThrownBy(() -> service.release(dario))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer eligible");
        db.update("UPDATE professor SET department_id = ? WHERE professor_id = ?", department, ARLO);

        observationDeadline("2026-10-20");
        assertThatThrownBy(() -> service.release(dario))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
        observationDeadline("2026-11-15");

        service.assign(dario, observerOf(ARLO));
        assertThatThrownBy(() -> service.release(dario))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already assigned");
        assertThat(list(dario).released()).isFalse();
    }

    @Test
    void aSignupWithNoEligibleObserversCannotBeReleased() {
        db.update("UPDATE observer SET active_status = FALSE WHERE cycle_id = 1");
        int dario = darioSignsUp();
        assertThat(list(dario).candidates()).isEmpty();
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=1");
        assertThatThrownBy(() -> service.release(dario))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No eligible observers");
        assertThat(list(dario).released()).isFalse();
    }

    @Test
    void assignRefusesWithoutAFormOrOnceThePeriodCloses() {
        int dario = darioSignsUp();
        db.update("UPDATE observation_template SET active_status = FALSE");
        assertThatThrownBy(() -> service.assign(dario, observerOf(LEANDER)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active observation template");
        observationDeadline("2026-10-20");
        assertThatThrownBy(() -> service.assign(dario, observerOf(LEANDER)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, dario))
                .isZero();
    }

    @Test
    void acceptingRechecksEligibilityAndTheFormButDecliningDoesNot() {
        int target = darioSignsUp();
        releaseAfterDeadline(target);
        var request = requests.create(target, observerOf(ARLO), null);

        int department = departmentOf(ARLO);
        db.update("UPDATE professor SET department_id = 2 WHERE professor_id = ?", ARLO);
        assertThatThrownBy(() -> service.respond(ARLO, request.requestId(), true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer eligible");
        db.update("UPDATE professor SET department_id = ? WHERE professor_id = ?", department, ARLO);

        db.update("UPDATE observation_template SET active_status = FALSE");
        assertThatThrownBy(() -> service.respond(ARLO, request.requestId(), true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active observation template");

        observationDeadline("2026-10-20");
        assertThatThrownBy(() -> service.respond(ARLO, request.requestId(), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
        observationDeadline("2026-11-15");

        // Declining creates no observation, so it needs no form.
        service.respond(ARLO, request.requestId(), false);
        assertThat(db.queryForObject(
                        "SELECT status FROM observer_request WHERE request_id=?", String.class, request.requestId()))
                .isEqualTo("DECLINED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isZero();
    }

    @Test
    void anObserverWithAnIncompleteTeachingScheduleIsNotACandidate() {
        // Leander's own Fall 2026 class loses its meeting days, so nobody can tell when he is free.
        db.update("""
                UPDATE course_section SET meeting_days = NULL WHERE semester_id = ?
                    AND section_id IN (SELECT section_id FROM teaching_history WHERE professor_id = ?)
                """, FALL_2026, LEANDER);
        assertThat(candidateProfessors(darioSignsUp())).doesNotContain(LEANDER).contains(ARLO);
    }

    @Test
    void listsAreCappedAtFiveStayStableAndReleasedListsStopGrowing() {
        int dario = darioSignsUp();
        assertThat(candidateProfessors(dario)).containsExactlyInAnyOrder(LEANDER, ARLO, AVERY, KEIRA);
        // Six people are eligible to observe Keira, so her list is capped at five.
        assertThat(candidateProfessors(KEIRA_SIGNUP))
                .hasSize(5)
                .isSubsetOf(LEANDER, ARLO, AVERY, XIOMARA, PERRIN, DARIO);
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
        service.respond(secondProfessor, next.requestId(), true);
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
        assertThat(requests.confirm(request.requestId()).status()).isEqualTo("CONFIRMED");
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
        assertThat(ObserverEligibilityService.days("Mon/Wed"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(ObserverEligibilityService.days("Tuesday, Thursday"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        assertThat(ObserverEligibilityService.days("TTh"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        // A schedule it cannot read means no known meetings, never a guess.
        assertThat(ObserverEligibilityService.days(null)).isEmpty();
        assertThat(ObserverEligibilityService.days(" ")).isEmpty();
        assertThat(ObserverEligibilityService.days("TBA")).isEmpty();
        assertThat(ObserverEligibilityService.level(null)).isEqualTo(-1);
        assertThat(ObserverEligibilityService.level("0345")).isEqualTo(-1);
        assertThat(ObserverEligibilityService.level("3345L")).isEqualTo(-1);
        assertThat(ObserverEligibilityService.level("6301")).isEqualTo(6000);
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
}
