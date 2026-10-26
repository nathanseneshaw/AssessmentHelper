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
        service.release(dario);
        assertThat(service.facultyState(DARIO).signup().candidates()).hasSize(4);
        // Hollis becomes eligible by teaching a CS 3345 section (TR 17:30), but Dario's list has been sent.
        db.update("INSERT INTO teaching_history (professor_id, section_id) VALUES (?, 300)", HOLLIS);
        signup(HOLLIS, 284);
        assertThat(candidateIds(dario)).containsExactlyElementsOf(darioIds);
    }

    @Test
    void filtersDepartmentLevelActivitySemesterAndTeachingOverlap() {
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
    void releaseThenOneRequestAtATimeWithDeclineAndAccept() {
        int target = darioSignsUp();
        var candidates = list(target).candidates();
        assertThat(candidates).hasSize(4);
        int first = candidates.get(0).observerId(), second = candidates.get(1).observerId();
        int firstProfessor = candidates.get(0).professorId(), secondProfessor = candidates.get(1).professorId();

        // Dario sees no candidates and cannot request until the committee releases the list.
        assertThat(options(DARIO).candidates()).isEmpty();
        assertThat(options(DARIO).unavailableReason()).isEqualTo("Your observer list is awaiting committee review.");
        assertThatThrownBy(() -> requests.create(target, first, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("awaiting committee review");
        service.release(target);
        assertThat(notificationCount(DARIO)).isEqualTo(1);
        assertThat(options(DARIO).candidates()).hasSize(4);
        assertThat(options(DARIO).unavailableReason()).isNull();

        int before = notificationCount(firstProfessor);
        var request = requests.create(target, first, null);
        assertThat(requests.create(target, first, null).requestId()).isEqualTo(request.requestId());
        assertThat(notificationCount(firstProfessor)).isEqualTo(before + 1);
        assertThatThrownBy(() -> requests.create(target, second, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a pending");
        assertThatThrownBy(() -> service.respond(secondProfessor, request.requestId(), true))
                .isInstanceOf(NotFoundException.class);

        service.respond(firstProfessor, request.requestId(), false);
        var next = requests.create(target, second, null);
        service.respond(secondProfessor, next.requestId(), true);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isEqualTo(1);
        assertThat(options(DARIO).unavailableReason()).isEqualTo("An observation is already scheduled for this signup.");
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
        service.release(target);
        var request = requests.create(target, list(target).candidates().getFirst().observerId(), null);
        assertThat(requests.confirm(request.requestId()).status()).isEqualTo("CONFIRMED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isEqualTo(1);
        assertThatThrownBy(() -> requests.confirm(request.requestId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsBadWindowsWrongClassMatchedChangesAndDeadlines() {
        assertThat(signups.window(DARIO, FALL_2026))
                .isEqualTo(new ObserverEligibilityService.Window(
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 15),
                        null,
                        LocalDate.of(2026, 10, 31),
                        true));
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
        service.release(target);
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
                .containsExactly("CS 3345.008 - Data Structures and Foundations of Algorithmic Analysis");
    }
}
