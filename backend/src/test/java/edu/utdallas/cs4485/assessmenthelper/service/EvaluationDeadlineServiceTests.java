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
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** The ASSISTANT (1 year) and ASSOCIATE/FULL (2 years) policies come from the seed data in data.sql. */
@DataJdbcTest
@Import({
    EvaluationDeadlineService.class,
    EvaluationPolicyService.class,
    EvaluationRecordService.class,
    EvaluationDeadlineServiceTests.FixedClock.class
})
class EvaluationDeadlineServiceTests {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private EvaluationDeadlineService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;

    @BeforeEach
    void createDepartment() {
        School school = db.insert(new School(null, "Test School"));
        department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
    }

    @Test
    void assistantIsDueOneYearAfterLastEvaluation() {
        Professor professor = professor("ASSISTANT", LocalDate.of(2020, 8, 15));
        completeEvaluation(professor, LocalDateTime.of(2026, 3, 10, 12, 0));

        assertThat(service.findByProfessor(professor.professorId()))
                .isEqualTo(new EvaluationDeadline(
                        professor.professorId(),
                        "ASSISTANT",
                        1,
                        LocalDate.of(2026, 3, 10),
                        LocalDate.of(2027, 3, 10),
                        false));
    }

    @Test
    void associateAndFullAreDueTwoYearsAfterLastEvaluation() {
        for (String rank : new String[] {"ASSOCIATE", "FULL"}) {
            Professor professor = professor(rank, LocalDate.of(2005, 8, 15));
            completeEvaluation(professor, LocalDateTime.of(2025, 5, 1, 9, 0));

            EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
            assertThat(deadline.evaluationIntervalYears()).isEqualTo(2);
            assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(deadline.overdue()).isFalse();
        }
    }

    @Test
    void latestCompletedEvaluationIsUsedAndIncompleteOnesAreIgnored() {
        Professor professor = professor("ASSISTANT", null);
        completeEvaluation(professor, LocalDateTime.of(2024, 1, 5, 9, 0));
        completeEvaluation(professor, LocalDateTime.of(2025, 2, 6, 9, 0));
        completeEvaluation(professor, null);

        assertThat(service.findByProfessor(professor.professorId()).lastEvaluationDate())
                .isEqualTo(LocalDate.of(2025, 2, 6));
    }

    @Test
    void pastDueDateIsOverdue() {
        Professor professor = professor("ASSISTANT", null);
        completeEvaluation(professor, LocalDateTime.of(2025, 1, 1, 9, 0));

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(deadline.overdue()).isTrue();
    }

    @Test
    void dueDateEarlierInCurrentSemesterIsDueNotOverdue() {
        // Issue #121: due 2026-09-15 is before today (2026-10-20) but inside Fall 2026, so not overdue.
        Professor professor = professor("ASSISTANT", null);
        completeEvaluation(professor, LocalDateTime.of(2025, 9, 15, 9, 0));

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(deadline.overdue()).isFalse();
    }

    @Test
    void observationWithoutCompletedObserveeStatusIsNotAnEvaluation() {
        Professor professor = professor("ASSISTANT", LocalDate.of(2024, 8, 15));
        completeEvaluation(professor, LocalDateTime.of(2026, 3, 10, 12, 0), "ACTIVE");

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.lastEvaluationDate()).isNull();
        assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2025, 8, 15));
        assertThat(deadline.overdue()).isTrue();
    }

    @Test
    void inactiveProfessorIsNeverOverdue() {
        Professor professor = db.insert(new Professor(
                null,
                department.departmentId(),
                "ASSISTANT",
                "Test",
                "Professor",
                "i" + System.nanoTime() + "@test.edu",
                null,
                false));
        completeEvaluation(professor, LocalDateTime.of(2025, 1, 1, 9, 0));

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(deadline.overdue()).isFalse();
    }

    @Test
    void neverEvaluatedProfessorIsDueOneIntervalAfterHire() {
        Professor professor = professor("FULL", LocalDate.of(2026, 8, 15));

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.lastEvaluationDate()).isNull();
        assertThat(deadline.nextDueDate()).isEqualTo(LocalDate.of(2028, 8, 15));
        assertThat(deadline.overdue()).isFalse();
    }

    @Test
    void neverEvaluatedProfessorWithoutHireDateHasNoDueDate() {
        Professor professor = professor("FULL", null);

        EvaluationDeadline deadline = service.findByProfessor(professor.professorId());
        assertThat(deadline.nextDueDate()).isNull();
        assertThat(deadline.overdue()).isFalse();
    }

    @Test
    void unknownProfessorThrowsNotFound() {
        assertThatThrownBy(() -> service.findByProfessor(-1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No professor with id -1");
    }

    @Test
    void professorWithoutRankThrowsNotFound() {
        Professor professor = professor(null, null);

        assertThatThrownBy(() -> service.findByProfessor(professor.professorId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("has no rank");
    }

    private Professor professor(String rank, LocalDate hireDate) {
        return db.insert(new Professor(
                null,
                department.departmentId(),
                rank,
                "Test",
                "Professor",
                "p" + System.nanoTime() + "@test.edu",
                hireDate,
                true));
    }

    /** Records one observation of the professor, completed at the given time or still scheduled if null. */
    private void completeEvaluation(Professor professor, LocalDateTime completedAt) {
        completeEvaluation(professor, completedAt, completedAt == null ? "ACTIVE" : "COMPLETED");
    }

    private void completeEvaluation(Professor professor, LocalDateTime completedAt, String observeeStatus) {
        Course course = db.insert(
                new Course(null, department.departmentId(), "T", "1" + System.nanoTime(), "C", "UNDERGRADUATE"));
        ObservationTemplate template = db.insert(new ObservationTemplate(null, null, "T", 1, null, true));
        Semester semester = db.insert(new Semester(null, "Fall", 2026, null, null));
        EvaluationCycle cycle = db.insert(new EvaluationCycle(null, semester.semesterId(), null, null, null, "OPEN"));
        Professor observerProfessor = professor("FULL", null);
        Observee observee = db.insert(new Observee(null, professor.professorId(), cycle.cycleId(), observeeStatus));
        Observer observer = db.insert(new Observer(null, observerProfessor.professorId(), cycle.cycleId(), true));
        CourseSection section = db.insert(
                new CourseSection(null, course.courseId(), semester.semesterId(), "001", null, null, null, null));
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
