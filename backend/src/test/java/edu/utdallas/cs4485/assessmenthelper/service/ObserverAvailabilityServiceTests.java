package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import(ObserverAvailabilityService.class)
class ObserverAvailabilityServiceTests {

    @Autowired
    private ObserverAvailabilityService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;
    private Course course;
    private Semester fall;
    private Semester spring;
    private Professor observerProfessor;
    private Observer observer;
    private EvaluationSignup signup;

    @BeforeEach
    void createSignupForMwfTenOClockClass() {
        School school = db.insert(new School(null, "Test School"));
        department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        course = db.insert(new Course(null, department.departmentId(), "T", "1000", "C", "UNDERGRADUATE"));
        fall = db.insert(new Semester(null, "Fall", 2026, null, null));
        spring = db.insert(new Semester(null, "Spring", 2027, null, null));
        EvaluationCycle cycle = db.insert(new EvaluationCycle(null, fall.semesterId(), null, null, null, "OPEN"));
        Professor observeeProfessor = professor("observee@test.edu");
        observerProfessor = professor("observer@test.edu");
        Observee observee = db.insert(new Observee(null, observeeProfessor.professorId(), cycle.cycleId(), "ACTIVE"));
        observer = db.insert(new Observer(null, observerProfessor.professorId(), cycle.cycleId(), true));
        CourseSection observed = section(fall, "MWF", "10:00", "10:50");
        signup = db.insert(new EvaluationSignup(null, observee.observeeId(), observed.sectionId(), null, null));
    }

    @Test
    void availableWhenObserverTeachesNothing() {
        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isTrue();
        assertThat(service.conflicts(observer.observerId(), signup.signupId())).isEmpty();
    }

    @Test
    void unavailableWhenSharedDayAndTimesOverlap() {
        CourseSection clash = observerTeaches(section(fall, "MW", "10:30", "11:45"));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isFalse();
        assertThat(service.conflicts(observer.observerId(), signup.signupId()))
                .extracting(CourseSection::sectionId)
                .containsExactly(clash.sectionId());
    }

    @Test
    void oneSharedDayIsEnough() {
        observerTeaches(section(fall, "TM", "09:30", "10:15"));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isFalse();
    }

    @Test
    void availableWhenMeetingDaysDiffer() {
        observerTeaches(section(fall, "TR", "10:00", "10:50"));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isTrue();
    }

    @Test
    void availableWhenClassesOnlyTouch() {
        observerTeaches(section(fall, "MWF", "09:00", "10:00"));
        observerTeaches(section(fall, "MWF", "10:50", "11:40"));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isTrue();
    }

    @Test
    void availableWhenSameTimeWasInAnotherSemester() {
        observerTeaches(section(spring, "MWF", "10:00", "10:50"));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isTrue();
    }

    @Test
    void sectionsWithoutAScheduleNeverConflict() {
        observerTeaches(section(fall, null, null, null));
        observerTeaches(section(fall, "MWF", null, null));

        assertThat(service.isAvailable(observer.observerId(), signup.signupId()))
                .isTrue();
    }

    @Test
    void unknownIdsThrowNotFoundAndMissingIdsThrowIllegalArgument() {
        assertThatThrownBy(() -> service.isAvailable(observer.observerId(), -1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No evaluation signup with id -1");
        assertThatThrownBy(() -> service.isAvailable(-1, signup.signupId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No observer with id -1");
        assertThatThrownBy(() -> service.isAvailable(null, signup.signupId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CourseSection section(Semester semester, String days, String start, String end) {
        return db.insert(new CourseSection(
                null,
                course.courseId(),
                semester.semesterId(),
                "001",
                days,
                start == null ? null : LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end),
                null));
    }

    private CourseSection observerTeaches(CourseSection section) {
        db.insert(new TeachingHistory(null, observerProfessor.professorId(), section.sectionId()));
        return section;
    }

    private Professor professor(String email) {
        return db.insert(new Professor(null, department.departmentId(), "FULL", "T", "P", email, null, true));
    }
}
