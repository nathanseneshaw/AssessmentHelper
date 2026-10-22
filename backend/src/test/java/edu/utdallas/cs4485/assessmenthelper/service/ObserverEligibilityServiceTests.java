package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.*;

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
@Import({ObserverEligibilityService.class, ObserverEligibilityServiceTests.Time.class})
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
    JdbcTemplate db;

    final LocalDate start = LocalDate.of(2026, 11, 1), end = LocalDate.of(2026, 11, 10);

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

    ObserverEligibilityService.Signup signup(int id) {
        return service.signup(100 + id, 100 + id, start, end);
    }

    @Test
    void poolGrowthClearsZeroAndListsStayStableWithFiveCandidateCap() {
        var target = signup(1);
        assertThat(target.candidates()).isEmpty();
        for (int i = 2; i <= 8; i++) signup(i);
        var list = service.acState().signups().getFirst();
        assertThat(list.candidates()).hasSize(5).allMatch(c -> c.professorId() != 101);
        var ids = list.candidates().stream()
                .map(ObserverEligibilityService.Candidate::observerId)
                .toList();
        service.generate();
        assertThat(service.acState().signups().getFirst().candidates().stream()
                        .map(ObserverEligibilityService.Candidate::observerId))
                .containsExactlyElementsOf(ids);
        assertThat(service.facultyState(101).signup().candidates()).isEmpty();
        service.release(target.signupId());
        signup(9);
        assertThat(service.facultyState(101).signup().candidates()).hasSize(5);
        assertThat(service.acState().signups().getFirst().candidates().stream()
                        .map(ObserverEligibilityService.Candidate::observerId))
                .containsExactlyElementsOf(ids);
    }

    @Test
    void filtersDepartmentLevelActivitySemesterAndTeachingOverlap() {
        db.update("UPDATE professor SET department_id=101 WHERE professor_id=102");
        db.update("UPDATE course_section SET course_id=101 WHERE section_id=103");
        db.update("UPDATE course_section SET start_time='10:30',end_time='11:30' WHERE section_id=104");
        db.update(
                "UPDATE course_section SET meeting_days='M',start_time='10:30',end_time='11:30' WHERE section_id=105");
        signup(1);
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
        db.update(
                "DELETE FROM observer_match WHERE signup_id=?",
                service.acState().signups().getFirst().signupId());
        service.generate();
        assertThat(service.acState().signups().getFirst().candidates())
                .extracting(ObserverEligibilityService.Candidate::professorId)
                .containsExactly(105);
    }

    @Test
    void releaseMultipleRequestsAcceptDeclineAndNotifications() {
        var target = signup(1);
        signup(2);
        signup(3);
        signup(4);
        var candidates = service.acState().signups().getFirst().candidates();
        var ids = candidates.stream()
                .map(ObserverEligibilityService.Candidate::observerId)
                .toList();
        assertThatThrownBy(() -> service.send(101, target.signupId(), ids)).isInstanceOf(ConflictException.class);
        service.release(target.signupId());
        assertThat(service.facultyState(101).notifications()).hasSize(1);
        service.send(101, target.signupId(), ids);
        service.send(101, target.signupId(), ids);
        assertThat(service.facultyState(101).outgoing()).hasSize(3);
        for (int i = 0; i < candidates.size(); i++) {
            int id = candidates.get(i).professorId();
            var request = service.facultyState(id).incoming().getFirst();
            assertThatThrownBy(() -> service.respond(101, request.requestId(), true))
                    .isInstanceOf(IllegalArgumentException.class);
            service.respond(id, request.requestId(), i < 2);
        }
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM observation WHERE signup_id=?", Integer.class, target.signupId()))
                .isEqualTo(2);
        assertThat(service.facultyState(101).outgoing())
                .extracting(ObserverEligibilityService.Request::status)
                .containsExactlyInAnyOrder("CONFIRMED", "CONFIRMED", "DECLINED");
        assertThat(service.facultyState(101).notifications()).hasSize(4);
    }

    @Test
    void rejectsSelfOutOfListDuplicatesWrongClassAndDeadlines() {
        assertThatThrownBy(() -> service.signup(101, 101, LocalDate.of(2026, 10, 31), end))
                .isInstanceOf(IllegalArgumentException.class);
        var target = signup(1);
        signup(2);
        assertThatThrownBy(() -> signup(1)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.signup(103, 101, start, end)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signup(103, 103, start, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        service.release(target.signupId());
        int self = db.queryForObject("SELECT observer_id FROM observer WHERE professor_id=101", Integer.class);
        assertThatThrownBy(() -> service.send(101, target.signupId(), List.of(self)))
                .isInstanceOf(ConflictException.class);
        db.update("UPDATE evaluation_cycle SET signup_deadline='2026-10-20' WHERE cycle_id=100");
        assertThatThrownBy(() -> signup(3)).isInstanceOf(ConflictException.class);
        // Lists can still be released and requests sent after signup closes.
        var candidate = service.acState().signups().getFirst().candidates().getFirst();
        service.send(101, target.signupId(), List.of(candidate.observerId()));
        db.update("UPDATE evaluation_cycle SET observation_deadline='2026-10-20' WHERE cycle_id=100");
        assertThatThrownBy(() -> service.send(101, target.signupId(), List.of(candidate.observerId())))
                .isInstanceOf(ConflictException.class);
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
        signup(1);
        signup(2);
        assertThat(service.acState().signups().getFirst().candidates()).hasSize(1);
        assertThat(service.acState()
                        .signups()
                        .getFirst()
                        .candidates()
                        .getFirst()
                        .qualifyingCourses())
                .containsExactly("CS 3346.2 - Data Structures");
    }
}
