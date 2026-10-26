package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;

import edu.utdallas.cs4485.assessmenthelper.repository.ObserverRequestContextRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJdbcTest(properties = "spring.sql.init.data-locations=classpath:empty.sql")
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

    @Autowired
    ObserverEligibilityService service;

    @Autowired
    EvaluationSignupService signups;

    @Autowired
    ObserverRequestService requests;

    @Autowired
    JdbcTemplate db;

    final LocalDate end = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setup() {
        db.update("INSERT INTO school(school_id,school_name) VALUES(100,'School')");
        db.update(
                "INSERT INTO department(department_id,school_id,department_name,department_code) VALUES(100,100,'CS','TCS'),(101,100,'Math','TM')");
        db.update(
                "INSERT INTO semester(semester_id,term,academic_year,start_date,end_date) VALUES(100,'Fall',2026,'2026-08-01','2026-12-31'),(101,'Spring',2026,'2026-01-01','2026-05-31')");
        db.update(
                "INSERT INTO evaluation_cycle(cycle_id,semester_id,signup_deadline,observation_deadline,status) VALUES(100,100,'2026-10-31','2026-11-15','OPEN')");
        db.update(
                "INSERT INTO course(course_id,department_id,course_code,course_number,course_name) VALUES(100,100,'CS','3345','Algorithms'),(101,100,'CS','4348','OS'),(102,101,'MATH','3345','Math')");
        db.update(
                "INSERT INTO observation_template(template_id,template_name,version,active_status) VALUES(100,'Template',1,TRUE)");
        for (int i = 1; i <= 12; i++) {
            db.update(
                    "INSERT INTO professor(professor_id,department_id,first_name,last_name,email,active_status) VALUES(?,100,'Professor',?,?,TRUE)",
                    100 + i,
                    "" + i,
                    "p" + i + "@test.edu");
            db.update(
                    "INSERT INTO course_section(section_id,course_id,semester_id,section_number,meeting_days,start_time,end_time) VALUES(?,100,100,?,'MW','08:30','09:45')",
                    100 + i,
                    "" + i);
            db.update("INSERT INTO teaching_history(professor_id,section_id) VALUES(?,?)", 100 + i, 100 + i);
        }
        // Target teaches MW 10:00; candidates' own MW 08:30 classes do not overlap.
        db.update("UPDATE course_section SET start_time='10:00',end_time='11:15' WHERE section_id=101");
    }

    int signup(int id) {
        return signups.save(100 + id, 100, 100 + id, end).signupId();
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

    ObserverRequestService.RequestOptions options(int professorId) {
        return requests.options(professorId).getFirst();
    }

    @Test
    void poolGrowthClearsZeroAndListsStayStableWithFiveCandidateCap() {
        int target = signup(1);
        assertThat(list(target).candidates()).isEmpty();
        for (int i = 2; i <= 8; i++) signup(i);
        assertThat(list(target).candidates()).hasSize(5).allMatch(c -> c.professorId() != 101);
        var ids = candidateIds(target);
        service.generate();
        assertThat(candidateIds(target)).containsExactlyElementsOf(ids);
        assertThat(service.facultyState(101).signup().candidates()).isEmpty();
        service.release(target);
        signup(9);
        assertThat(service.facultyState(101).signup().candidates()).hasSize(5);
        assertThat(candidateIds(target)).containsExactlyElementsOf(ids);
    }

    @Test
    void filtersDepartmentLevelActivitySemesterAndTeachingOverlap() {
        db.update("UPDATE professor SET department_id=101 WHERE professor_id=102");
        db.update("UPDATE course_section SET course_id=101 WHERE section_id=103");
        db.update("UPDATE course_section SET start_time='10:30',end_time='11:30' WHERE section_id=104");
        db.update(
                "UPDATE course_section SET meeting_days='M',start_time='10:30',end_time='11:30' WHERE section_id=105");
        int target = signup(1);
        signup(2);
        signup(3);
        signup(4);
        signup(5);
        // inactive professor and inactive observer must not qualify, even with existing signup.
        signup(6);
        db.update("UPDATE professor SET active_status=FALSE WHERE professor_id=106");
        signup(7);
        db.update("UPDATE observer SET active_status=FALSE WHERE professor_id=107");
        signup(8);
        db.update("UPDATE course_section SET semester_id=101 WHERE section_id=108");
        // Rebuild target's unreleased list after deliberately changing fixture assignments.
        db.update("DELETE FROM observer_match WHERE signup_id=?", target);
        service.generate();
        assertThat(list(target).candidates())
                .extracting(ObserverEligibilityService.Candidate::professorId)
                .containsExactly(105);
    }

    @Test
    void releaseThenOneRequestAtATimeWithDeclineAndAccept() {
        int target = signup(1);
        signup(2);
        signup(3);
        signup(4);
        var candidates = list(target).candidates();
        assertThat(candidates).hasSize(3);
        int first = candidates.get(0).observerId(), second = candidates.get(1).observerId();
        int firstProfessor = candidates.get(0).professorId(), secondProfessor = candidates.get(1).professorId();

        // The observee sees no candidates and cannot request until the committee releases the list.
        assertThat(options(101).candidates()).isEmpty();
        assertThat(options(101).unavailableReason()).isEqualTo("Your observer list is awaiting committee review.");
        assertThatThrownBy(() -> requests.create(target, first, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("awaiting committee review");
        service.release(target);
        assertThat(service.facultyState(101).notifications()).hasSize(1);
        assertThat(options(101).candidates()).hasSize(3);
        assertThat(options(101).unavailableReason()).isNull();

        var request = requests.create(target, first, null);
        assertThat(requests.create(target, first, null).requestId()).isEqualTo(request.requestId());
        assertThat(service.facultyState(firstProfessor).notifications()).hasSize(1);
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
        assertThat(options(101).unavailableReason()).isEqualTo("An observation is already scheduled for this signup.");
        assertThatThrownBy(() -> requests.create(target, candidates.get(2).observerId(), null))
                .isInstanceOf(ConflictException.class);
        assertThat(service.facultyState(101).outgoing())
                .extracting(ObserverEligibilityService.Request::status)
                .containsExactlyInAnyOrder("CONFIRMED", "DECLINED");
        assertThat(service.facultyState(101).notifications()).hasSize(3);
    }

    @Test
    void confirmingThroughTheRequestApiRecordsTheObservation() {
        int target = signup(1);
        signup(2);
        service.release(target);
        var request = requests.create(target, list(target).candidates().getFirst().observerId(), null);
        assertThat(requests.confirm(request.requestId()).status()).isEqualTo("CONFIRMED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target))
                .isEqualTo(1);
        assertThatThrownBy(() -> requests.confirm(request.requestId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsBadWindowsWrongClassMatchedChangesAndDeadlines() {
        assertThat(signups.window(101, 100))
                .isEqualTo(new ObserverEligibilityService.Window(
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 15),
                        null,
                        LocalDate.of(2026, 10, 31),
                        true));
        assertThatThrownBy(() -> signups.save(101, 100, 101, LocalDate.of(2026, 10, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signups.save(101, 100, 101, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        // Nov 1 is a Sunday, so a one-day window has no MW meeting.
        assertThatThrownBy(() -> signups.save(101, 100, 101, LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no class meetings");
        db.update("UPDATE course_section SET meeting_days=NULL WHERE section_id=112");
        assertThatThrownBy(() -> signups.save(112, 100, 112, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete meeting schedule");

        int target = signup(1);
        assertThat(signups.window(101, 100).end()).isEqualTo(end);
        assertThat(signups.save(101, 100, 101, null).signupId()).isEqualTo(target);
        assertThatThrownBy(() -> signups.save(103, 100, 101, end)).isInstanceOf(IllegalArgumentException.class);
        signup(2);
        // Once candidates exist, the selection is locked.
        assertThatThrownBy(() -> signups.save(101, 100, 101, LocalDate.of(2026, 11, 12)))
                .isInstanceOf(ConflictException.class);
        service.release(target);
        int self = db.queryForObject("SELECT observer_id FROM observer WHERE professor_id=101", Integer.class);
        assertThatThrownBy(() -> requests.create(target, self, null)).isInstanceOf(ConflictException.class);

        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=100");
        assertThatThrownBy(() -> signup(3)).isInstanceOf(ConflictException.class);
        // Lists can still be released and requests sent after signup closes.
        int candidate = list(target).candidates().getFirst().observerId();
        requests.create(target, candidate, null);
        db.update("DELETE FROM observer_request WHERE signup_id=?", target);
        db.update("UPDATE evaluation_cycle SET observation_deadline='2026-10-20' WHERE cycle_id=100");
        assertThatThrownBy(() -> requests.create(target, candidate, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("observation period is closed");
    }

    @Test
    void scheduleParsingAndBoundaryAvailability() {
        assertThat(ObserverEligibilityService.days("Monday / Wednesday"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(ObserverEligibilityService.days("TR"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        assertThat(ObserverEligibilityService.level("3345")).isEqualTo(3000);
        assertThat(ObserverEligibilityService.level("101")).isEqualTo(-1);
        db.update("UPDATE course SET course_number='3346',course_name='Data Structures' WHERE course_id=101");
        db.update("UPDATE course_section SET course_id=101,end_time='10:00' WHERE section_id=102");
        int target = signup(1);
        signup(2);
        assertThat(list(target).candidates()).hasSize(1);
        assertThat(list(target).candidates().getFirst().qualifyingCourses())
                .containsExactly("CS 3346.2 - Data Structures");
    }
}
