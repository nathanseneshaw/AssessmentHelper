package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationCycleRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import edu.utdallas.cs4485.assessmenthelper.service.CsvImportService.Row;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult.RowError;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class SectionCsvImporter {

    static final List<String> COLUMNS = List.of(
            "course_prefix",
            "course_number",
            "course_name",
            "course_level",
            "department_code",
            "term",
            "year",
            "section",
            "days",
            "start_time",
            "end_time",
            "location",
            "instructor_email");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm[:ss]");

    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final SemesterRepository semesters;
    private final DepartmentRepository departments;
    private final ProfessorRepository professors;
    private final TeachingHistoryRepository teachingHistory;
    private final EvaluationCycleRepository cycles;
    private final Clock clock;

    SectionCsvImporter(
            CourseRepository courses,
            CourseSectionRepository sections,
            SemesterRepository semesters,
            DepartmentRepository departments,
            ProfessorRepository professors,
            TeachingHistoryRepository teachingHistory,
            EvaluationCycleRepository cycles,
            Clock clock) {
        this.courses = courses;
        this.sections = sections;
        this.semesters = semesters;
        this.departments = departments;
        this.professors = professors;
        this.teachingHistory = teachingHistory;
        this.cycles = cycles;
        this.clock = clock;
    }

    void importRow(Row row, List<RowError> errors) {
        int before = errors.size();
        String prefix = upperCase(row.required("course_prefix", errors));
        String number = row.required("course_number", errors);
        String level = upperCase(row.get("course_level"));
        if (level != null && !level.equals("UNDERGRADUATE") && !level.equals("GRADUATE"))
            errors.add(row.error(
                    "course_level",
                    "Unknown course level '" + row.get("course_level") + "'. Use UNDERGRADUATE or GRADUATE."));
        String sectionNumber = row.required("section", errors);
        Semester semester = semester(row, errors);
        LocalTime start = time(row, "start_time", errors);
        LocalTime end = time(row, "end_time", errors);
        Professor instructor = instructor(row, errors);
        if (errors.size() > before) return;

        Course course = courses.findFirstByCourseCodeIgnoreCaseAndCourseNumberOrderByCourseId(prefix, number)
                .orElse(null);
        if (course == null) course = createCourse(row, prefix, number, level, errors);
        if (course == null) return;

        Optional<CourseSection> stored = sections.findFirstByCourseIdAndSemesterIdAndSectionNumberOrderBySectionId(
                course.courseId(), semester.semesterId(), sectionNumber);
        String days = upperCase(row.get("days"));
        String location = row.get("location");
        CourseSection section = sections.save(new CourseSection(
                stored.map(CourseSection::sectionId).orElse(null),
                course.courseId(),
                semester.semesterId(),
                sectionNumber,
                days != null ? days : stored.map(CourseSection::meetingDays).orElse(null),
                start != null ? start : stored.map(CourseSection::startTime).orElse(null),
                end != null ? end : stored.map(CourseSection::endTime).orElse(null),
                location != null
                        ? location
                        : stored.map(CourseSection::location).orElse(null)));

        if (instructor != null
                && !teachingHistory.existsByProfessorIdAndSectionId(instructor.professorId(), section.sectionId()))
            teachingHistory.save(new TeachingHistory(null, instructor.professorId(), section.sectionId()));
    }

    private Course createCourse(Row row, String prefix, String number, String level, List<RowError> errors) {
        String name = row.get("course_name");
        if (name == null)
            errors.add(row.error(
                    "course_name", "course_name is required for a new course (" + prefix + " " + number + ")."));
        String code = Optional.ofNullable(row.get("department_code")).orElse(prefix);
        Optional<Department> department = departments.findByDepartmentCodeIgnoreCase(code);
        if (department.isEmpty()) errors.add(row.error("department_code", "Unknown department code '" + code + "'."));
        if (name == null || department.isEmpty()) return null;
        if (level == null) level = number.charAt(0) >= '5' ? "GRADUATE" : "UNDERGRADUATE";
        return courses.save(new Course(null, department.get().departmentId(), prefix, number, name, level));
    }

    private Semester semester(Row row, List<RowError> errors) {
        String term = row.required("term", errors);
        String year = row.required("year", errors);
        if (term == null || year == null) return null;
        Optional<Semester> semester = year.matches("\\d{4}")
                ? semesters.findFirstByTermIgnoreCaseAndAcademicYearOrderBySemesterId(term, Integer.valueOf(year))
                : Optional.empty();
        if (semester.isEmpty()) errors.add(row.error("term", "No " + term + " " + year + " semester exists."));
        if (semester.isPresent()
                && semester.get().endDate() != null
                && LocalDate.now(clock).isAfter(semester.get().endDate())
                && cycles.findAll().stream()
                        .anyMatch(cycle ->
                                cycle.semesterId().equals(semester.get().semesterId()))) {
            errors.add(row.error("term", "Sections in a past evaluation cycle are read-only."));
            return null;
        }
        return semester.orElse(null);
    }

    private static LocalTime time(Row row, String column, List<RowError> errors) {
        String value = row.get(column);
        if (value == null) return null;
        try {
            return LocalTime.parse(value, TIME);
        } catch (DateTimeParseException e) {
            errors.add(row.error(column, "'" + value + "' is not a time. Use HH:mm, such as 13:00."));
            return null;
        }
    }

    private Professor instructor(Row row, List<RowError> errors) {
        String email = row.get("instructor_email");
        if (email == null) return null;
        Optional<Professor> instructor = professors.findByEmailIgnoreCase(email);
        if (instructor.isEmpty())
            errors.add(row.error(
                    "instructor_email", "No professor with email " + email + ". Upload them in professors.csv first."));
        return instructor.orElse(null);
    }

    private static String upperCase(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
