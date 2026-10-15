package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationCycle;
import edu.utdallas.cs4485.assessmenthelper.entity.EvaluationSignup;
import edu.utdallas.cs4485.assessmenthelper.entity.Observation;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationTemplate;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.entity.Observer;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import(EvaluationRecordService.class)
class EvaluationRecordServiceTests {

    @Autowired
    private EvaluationRecordService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;
    private Course course;
    private ObservationTemplate template;
    private Professor observeeProfessor;
    private Professor observerProfessor;
    private EvaluationCycle fall2026;
    private EvaluationCycle spring2025;

    @BeforeEach
    void createEvaluationHistory() {
        department = db.insert(new Department(null, "Test Department", "TEST"));
        course = db.insert(new Course(null, department.departmentId(), "TEST", "1000", "Test Course", "UNDERGRADUATE"));
        template = db.insert(new ObservationTemplate(null, null, "Test Template", 1, null, true));
        observeeProfessor = db.insert(professor("observee@test.edu"));
        observerProfessor = db.insert(professor("observer@test.edu"));

        Semester fallSemester =
                db.insert(new Semester(null, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 11)));
        Semester springSemester =
                db.insert(new Semester(null, "Spring", 2025, LocalDate.of(2025, 1, 13), LocalDate.of(2025, 5, 9)));
        fall2026 = db.insert(new EvaluationCycle(null, fallSemester.semesterId(), null, null, null, "OPEN"));
        spring2025 = db.insert(new EvaluationCycle(null, springSemester.semesterId(), null, null, null, "CLOSED"));

        Observee fallObservee = observee(fall2026, "ACTIVE");
        Observee springObservee = observee(spring2025, "COMPLETED");

        observe(fallObservee, section(fallSemester, "001"), null);
        observe(springObservee, section(springSemester, "001"), LocalDateTime.of(2025, 3, 10, 12, 0));
        observe(springObservee, section(springSemester, "002"), LocalDateTime.of(2025, 4, 2, 15, 30));
    }

    @Test
    void returnsOneRecordPerCycleNewestFirst() {
        assertThat(service.findByProfessor(observeeProfessor.professorId()))
                .containsExactly(
                        new EvaluationRecord(fall2026.cycleId(), "Fall", 2026, "ACTIVE", 1, null),
                        new EvaluationRecord(
                                spring2025.cycleId(),
                                "Spring",
                                2025,
                                "COMPLETED",
                                2,
                                LocalDateTime.of(2025, 4, 2, 15, 30)));
    }

    @Test
    void professorWhoWasNeverObservedHasNoRecords() {
        assertThat(service.findByProfessor(observerProfessor.professorId())).isEmpty();
    }

    @Test
    void observeeWithoutSignupsHasNoObservations() {
        Professor newObservee = db.insert(professor("new-observee@test.edu"));
        db.insert(new Observee(null, newObservee.professorId(), fall2026.cycleId(), "ACTIVE"));

        assertThat(service.findByProfessor(newObservee.professorId()))
                .containsExactly(new EvaluationRecord(fall2026.cycleId(), "Fall", 2026, "ACTIVE", 0, null));
    }

    @Test
    void unknownProfessorThrowsNotFound() {
        assertThatThrownBy(() -> service.findByProfessor(-1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No professor with id -1");
    }

    private Professor professor(String email) {
        return new Professor(null, department.departmentId(), null, "Test", "Professor", email, null, true);
    }

    private Observee observee(EvaluationCycle cycle, String status) {
        return db.insert(new Observee(null, observeeProfessor.professorId(), cycle.cycleId(), status));
    }

    private CourseSection section(Semester semester, String sectionNumber) {
        return db.insert(new CourseSection(
                null, course.courseId(), semester.semesterId(), sectionNumber, null, null, null, null));
    }

    private void observe(Observee observee, CourseSection section, LocalDateTime completedAt) {
        Observer observer = db.insert(new Observer(null, observerProfessor.professorId(), observee.cycleId(), true));
        EvaluationSignup signup =
                db.insert(new EvaluationSignup(null, observee.observeeId(), section.sectionId(), null, null));
        db.insert(new Observation(
                null,
                signup.signupId(),
                observer.observerId(),
                template.templateId(),
                null,
                null,
                completedAt == null ? "SCHEDULED" : "COMPLETED",
                false,
                false,
                null,
                null,
                completedAt));
    }
}
