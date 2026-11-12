package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import(TeachingHistoryService.class)
class TeachingHistoryServiceTests {

    @Autowired
    private TeachingHistoryService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;
    private Semester spring2025;
    private Semester fall2026;

    @BeforeEach
    void createDepartmentAndSemesters() {
        School school = db.insert(new School(null, "Test School"));
        department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        spring2025 =
                db.insert(new Semester(null, "Spring", 2025, LocalDate.of(2025, 1, 21), LocalDate.of(2025, 5, 16)));
        fall2026 = db.insert(new Semester(null, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 16)));
    }

    @Test
    void returnsTheSectionsWithCourseAndSemesterDetails() {
        Professor professor = professor("ann");
        Course course = course("4485", "Computer Science Project");
        CourseSection section = teach(professor, course, fall2026, "001");

        assertThat(service.findByProfessor(professor.professorId()))
                .containsExactly(new TeachingHistoryEntry(
                        section.sectionId(),
                        course.courseId(),
                        "CS",
                        "4485",
                        "Computer Science Project",
                        4000,
                        "001",
                        "Fall",
                        2026,
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 12, 16)));
    }

    @Test
    void newestSemesterComesFirstThenCourseNumberThenSectionNumber() {
        Professor professor = professor("ann");
        CourseSection oldSection = teach(professor, course("2336", "Discrete Mathematics"), spring2025, "001");
        Course seniorCourse = course("4485", "Computer Science Project");
        CourseSection section002 = teach(professor, seniorCourse, fall2026, "002");
        CourseSection section001 = teach(professor, seniorCourse, fall2026, "001");
        CourseSection lowerCourse = teach(professor, course("3345", "Data Structures"), fall2026, "001");

        assertThat(service.findByProfessor(professor.professorId()))
                .extracting(TeachingHistoryEntry::sectionId)
                .containsExactly(
                        lowerCourse.sectionId(),
                        section001.sectionId(),
                        section002.sectionId(),
                        oldSection.sectionId());
    }

    @Test
    void semestersWithoutAStartDateComeLast() {
        Professor professor = professor("ann");
        Semester undated = db.insert(new Semester(null, "Fall", 2027, null, null));
        CourseSection undatedSection = teach(professor, course("4485", "Computer Science Project"), undated, "001");
        CourseSection datedSection = teach(professor, course("3345", "Data Structures"), spring2025, "001");

        assertThat(service.findByProfessor(professor.professorId()))
                .extracting(TeachingHistoryEntry::sectionId)
                .containsExactly(datedSection.sectionId(), undatedSection.sectionId());
    }

    @Test
    void onlyTheProfessorsOwnSectionsAreReturned() {
        Professor ann = professor("ann");
        Professor bea = professor("bea");
        CourseSection annSection = teach(ann, course("4485", "Computer Science Project"), fall2026, "001");
        teach(bea, course("3345", "Data Structures"), fall2026, "001");

        assertThat(service.findByProfessor(ann.professorId()))
                .extracting(TeachingHistoryEntry::sectionId)
                .containsExactly(annSection.sectionId());
    }

    @Test
    void coTaughtSectionAppearsInEachInstructorsHistory() {
        Professor ann = professor("ann");
        Professor bea = professor("bea");
        CourseSection shared = teach(ann, course("4485", "Computer Science Project"), fall2026, "001");
        db.insert(new TeachingHistory(null, bea.professorId(), shared.sectionId()));

        assertThat(service.findByProfessor(ann.professorId()))
                .extracting(TeachingHistoryEntry::sectionId)
                .containsExactly(shared.sectionId());
        assertThat(service.findByProfessor(bea.professorId()))
                .extracting(TeachingHistoryEntry::sectionId)
                .containsExactly(shared.sectionId());
    }

    @Test
    void levelIsTheThousandsOfTheCourseNumberOrNullWhenItIsNotFourDigits() {
        Professor professor = professor("ann");
        teach(professor, course("2336", "Discrete Mathematics"), fall2026, "001");
        teach(professor, course("5330", "Graduate Topics"), fall2026, "001");
        teach(professor, course("4V95", "Special Topics"), fall2026, "001");

        assertThat(service.findByProfessor(professor.professorId()))
                .extracting(TeachingHistoryEntry::courseNumber, TeachingHistoryEntry::level)
                .containsExactlyInAnyOrder(tuple("2336", 2000), tuple("5330", 5000), tuple("4V95", null));
    }

    @Test
    void professorWhoNeverTaughtGetsAnEmptyList() {
        Professor professor = professor("ann");

        assertThat(service.findByProfessor(professor.professorId())).isEmpty();
    }

    @Test
    void unknownProfessorThrowsNotFound() {
        assertThatThrownBy(() -> service.findByProfessor(-1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No professor with id -1");
    }

    private Professor professor(String name) {
        return db.insert(new Professor(
                null, department.departmentId(), "ASSISTANT", name, "Test", name + ".test@test.edu", null, true));
    }

    private Course course(String number, String name) {
        return db.insert(new Course(null, department.departmentId(), "CS", number, name, "UNDERGRADUATE"));
    }

    private CourseSection teach(Professor professor, Course course, Semester semester, String sectionNumber) {
        CourseSection section = db.insert(new CourseSection(
                null, course.courseId(), semester.semesterId(), sectionNumber, null, null, null, null));
        db.insert(new TeachingHistory(null, professor.professorId(), section.sectionId()));
        return section;
    }
}
