package edu.utdallas.cs4485.assessmenthelper.service;

import static edu.utdallas.cs4485.assessmenthelper.service.ProfessorCsvImportTests.csv;
import static org.assertj.core.api.Assertions.assertThat;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult.RowError;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@DataJdbcTest
@Import({CsvImportService.class, ProfessorCsvImporter.class, SectionCsvImporter.class})
class SectionCsvImportTests {

    private static final String HEADER = "course_prefix,course_number,course_name,course_level,department_code,"
            + "term,year,section,days,start_time,end_time,location,instructor_email";
    private static final String TAMSIN = "tamsin.orrell@example.edu";

    @Autowired
    private CsvImportService service;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CourseSectionRepository sections;

    @Autowired
    private SemesterRepository semesters;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private TeachingHistoryRepository teachingHistory;

    @Autowired
    private JdbcAggregateTemplate db;

    private Professor tamsin;

    @BeforeEach
    void addAnInstructor() {
        tamsin = db.insert(new Professor(null, departmentId("CS"), null, "Tamsin", "Orrell", TAMSIN, null, true));
    }

    @Test
    void createsTheCourseTheSectionAndTheTeachingAssignment() {
        ImportResult result = save(
                HEADER,
                "cs,4390,Computer Networks,,,fall,2026,001,tr,10:00,11:15:00,ECSS 2.410,Tamsin.Orrell@Example.edu");

        assertThat(result.errors()).isEmpty();
        assertThat(result.saved()).isTrue();
        Course course = course("CS", "4390").orElseThrow();
        assertThat(course)
                .extracting(Course::departmentId, Course::courseCode, Course::courseName, Course::courseLevel)
                .containsExactly(departmentId("CS"), "CS", "Computer Networks", "UNDERGRADUATE");
        CourseSection section = section(course, "Fall", 2026, "001").orElseThrow();
        assertThat(section)
                .extracting(
                        CourseSection::meetingDays,
                        CourseSection::startTime,
                        CourseSection::endTime,
                        CourseSection::location)
                .containsExactly("TR", LocalTime.of(10, 0), LocalTime.of(11, 15), "ECSS 2.410");
        assertThat(teachingHistory.existsByProfessorIdAndSectionId(tamsin.professorId(), section.sectionId()))
                .isTrue();
    }

    @Test
    void importingTheSameRowAgainChangesNothing() {
        String row = "CS,1337,,,,Fall,2022,0L1,,,,," + TAMSIN;
        save(HEADER, row);
        long courseCount = courses.count();
        long sectionCount = sections.count();
        long assignmentCount = teachingHistory.count();

        ImportResult again = save(HEADER, row);

        assertThat(again.errors()).isEmpty();
        assertThat(courses.count()).isEqualTo(courseCount);
        assertThat(sections.count()).isEqualTo(sectionCount);
        assertThat(teachingHistory.count()).isEqualTo(assignmentCount);
    }

    @Test
    void theInstructorMustAlreadyBeAProfessorAndNothingIsSavedOtherwise() {
        long sectionCount = sections.count();

        ImportResult result = save(
                HEADER,
                "CS,4390,Computer Networks,,,Fall,2026,001,,,,," + TAMSIN,
                "CS,4390,Computer Networks,,,Fall,2026,002,,,,,nobody.known@example.edu");

        assertThat(result.saved()).isFalse();
        assertThat(result.errors())
                .containsExactly(new RowError(
                        3,
                        "instructor_email",
                        "No professor with email nobody.known@example.edu. Upload them in professors.csv first."));
        assertThat(course("CS", "4390")).isEmpty();
        assertThat(sections.count()).isEqualTo(sectionCount);
    }

    @Test
    void theSemesterMustAlreadyExist() {
        ImportResult result = save(HEADER, "CS,1337,,,,Fall,2030,001,,,,,", "CS,1337,,,,Winter,2026,001,,,,,");

        assertThat(result.errors())
                .containsExactly(
                        new RowError(2, "term", "No Fall 2030 semester exists."),
                        new RowError(3, "term", "No Winter 2026 semester exists."));
    }

    @Test
    void badCellsAreReportedWithTheirRowAndColumn() {
        ImportResult result = save(
                HEADER,
                ",4390,Computer Networks,masters,,Fall,26,,,,,,",
                "CS,4390,Computer Networks,,CSS,Fall,2026,001,,,,,");

        assertThat(result.errors())
                .containsExactly(
                        new RowError(2, "course_prefix", "course_prefix is required."),
                        new RowError(
                                2, "course_level", "Unknown course level 'masters'. Use UNDERGRADUATE or GRADUATE."),
                        new RowError(2, "section", "section is required."),
                        new RowError(2, "term", "No Fall 26 semester exists."),
                        new RowError(3, "department_code", "Unknown department code 'CSS'."));
    }

    private ImportResult save(String... lines) {
        return service.importSections(csv(lines));
    }

    private Optional<Course> course(String prefix, String number) {
        return courses.findFirstByCourseCodeIgnoreCaseAndCourseNumberOrderByCourseId(prefix, number);
    }

    private Optional<CourseSection> section(Course course, String term, int year, String number) {
        int semesterId = semesters
                .findFirstByTermIgnoreCaseAndAcademicYearOrderBySemesterId(term, year)
                .orElseThrow()
                .semesterId();
        return sections.findFirstByCourseIdAndSemesterIdAndSectionNumberOrderBySectionId(
                course.courseId(), semesterId, number);
    }

    private Integer departmentId(String code) {
        return departments.findByDepartmentCodeIgnoreCase(code).orElseThrow().departmentId();
    }
}
