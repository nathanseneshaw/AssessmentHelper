package edu.utdallas.cs4485.assessmenthelper.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.CourseSectionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.DepartmentRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.SemesterRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObserverCandidateService {

    /** The most candidates suggested for one observee. Larger pools are sampled at random (issue #41). */
    static final int MAX_CANDIDATES = 5;

    /** An optional department code and the course number: {@code 4348}, {@code CS 4348} or {@code CS4348}. */
    private static final Pattern COURSE = Pattern.compile("([A-Za-z]*)\\s*(\\S+)");

    /** Course numbers are at most 9 digits so that they always fit in an int. */
    private static final Pattern NUMERIC_COURSE_NUMBER = Pattern.compile("\\d{1,9}");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final TeachingHistoryRepository teachingHistoryRepository;
    private final ProfessorRepository professorRepository;
    private final SemesterRepository semesterRepository;
    private final DepartmentRepository departmentRepository;

    public ObserverCandidateService(
            CourseRepository courseRepository,
            CourseSectionRepository sectionRepository,
            TeachingHistoryRepository teachingHistoryRepository,
            ProfessorRepository professorRepository,
            SemesterRepository semesterRepository,
            DepartmentRepository departmentRepository) {
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.teachingHistoryRepository = teachingHistoryRepository;
        this.professorRepository = professorRepository;
        this.semesterRepository = semesterRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Suggests up to {@value #MAX_CANDIDATES} professors who could observe the given professor teaching the given
     * section.
     *
     * <p>The observed section is the section with this number of a course with this number that the observee teaches.
     * The course number may carry a department code ({@code CS 4348} or {@code CS4348}, any case), which then must
     * match the course's code too. The professor name is the observee's first and last name, compared without regard to
     * case or extra whitespace. If the observee taught that section in several semesters, the semester with the latest
     * start date wins. The course level is the course number rounded down to the thousand (4348 is level 4000).
     *
     * <p>A professor is a candidate if all of these hold:
     *
     * <ul>
     *   <li>They are not the observee and they are active.
     *   <li>They belong to the observed course's department, and in the observed semester they teach at least one
     *       section of a course in that department whose level is the same as or higher than the observed course's
     *       level. Courses whose number is not numeric never qualify anyone.
     *   <li>None of the sections they teach in the observed semester meets at the same time as the observed section
     *       (see {@link SectionSchedules#conflict}). Teaching the observed section itself also counts as a conflict.
     * </ul>
     *
     * <p>If more than {@value #MAX_CANDIDATES} professors qualify, {@value #MAX_CANDIDATES} of them are picked at random
     * so that the observation load is spread fairly. Otherwise all of them are returned, which may be none. Either way
     * the list is sorted by last name, then first name.
     *
     * <p>Impl note: Like {@link EvaluationRecordService#findByProfessor}, the rows are loaded in batches (the observed
     * section first, then every section of its semester with its courses and teachers) and matched up in memory, so the
     * number of queries does not grow with the size of the department.
     *
     * @throws InvalidRequestException if an argument is blank, or the course number is not numeric apart from its
     *     department code
     * @throws NotFoundException if no such course or section exists, or the named professor does not teach it
     */
    @Transactional(readOnly = true)
    public List<ObserverCandidate> findCandidates(String courseNumber, String sectionNumber, String professorName) {
        String course = requireText(courseNumber, "courseNumber");
        String section = requireText(sectionNumber, "sectionNumber");
        String name = requireText(professorName, "professorName");

        Matcher matcher = COURSE.matcher(course);
        OptionalInt parsedLevel = matcher.matches() ? courseLevel(matcher.group(2)) : OptionalInt.empty();
        if (parsedLevel.isEmpty()) {
            throw new InvalidRequestException("courseNumber must be a number, optionally after a department code"
                    + " (such as 4348 or CS 4348), but was '" + course + "'");
        }
        int observedLevel = parsedLevel.getAsInt();
        String departmentCode = matcher.group(1);
        String number = matcher.group(2);
        String courseLabel = departmentCode.isEmpty() ? number : departmentCode.toUpperCase(Locale.ROOT) + " " + number;

        Map<Integer, Course> coursesById = courseRepository.findByCourseNumber(number).stream()
                .filter(candidate ->
                        departmentCode.isEmpty() || departmentCode.equalsIgnoreCase(candidate.courseCode()))
                .collect(toMap(Course::courseId, identity()));
        if (coursesById.isEmpty()) {
            throw new NotFoundException("No course " + courseLabel);
        }

        Map<Integer, CourseSection> sectionsById =
                sectionRepository.findByCourseIdInAndSectionNumber(coursesById.keySet(), section).stream()
                        .collect(toMap(CourseSection::sectionId, identity()));
        if (sectionsById.isEmpty()) {
            throw new NotFoundException("No section " + section + " of course " + courseLabel);
        }

        List<TeachingHistory> teachings = teachingHistoryRepository.findBySectionIdIn(sectionsById.keySet());
        String normalizedName = normalizeName(name);
        Map<Integer, Professor> observeesById =
                professorRepository
                        .findAllById(teachings.stream()
                                .map(TeachingHistory::professorId)
                                .collect(toSet()))
                        .stream()
                        .filter(professor -> normalizeName(professor.firstName() + " " + professor.lastName())
                                .equals(normalizedName))
                        .collect(toMap(Professor::professorId, identity()));
        List<TeachingHistory> observeeTeachings = teachings.stream()
                .filter(teaching -> observeesById.containsKey(teaching.professorId()))
                .toList();
        if (observeeTeachings.isEmpty()) {
            throw new NotFoundException(
                    "No professor named " + name + " teaches section " + section + " of course " + courseLabel);
        }

        Map<Integer, Semester> semestersById = semesterRepository
                .findAllById(observeeTeachings.stream()
                        .map(teaching -> sectionsById.get(teaching.sectionId()).semesterId())
                        .collect(toSet()))
                .stream()
                .collect(toMap(Semester::semesterId, identity()));
        Comparator<CourseSection> latestSemesterLast = comparing(
                        (CourseSection candidate) ->
                                semestersById.get(candidate.semesterId()).startDate(),
                        nullsFirst(naturalOrder()))
                .thenComparing(CourseSection::sectionId);
        TeachingHistory observed = observeeTeachings.stream()
                .max(comparing(teaching -> sectionsById.get(teaching.sectionId()), latestSemesterLast))
                .orElseThrow();
        CourseSection observedSection = sectionsById.get(observed.sectionId());
        Integer departmentId = coursesById.get(observedSection.courseId()).departmentId();

        Map<Integer, CourseSection> semesterSectionsById =
                sectionRepository.findBySemesterId(observedSection.semesterId()).stream()
                        .collect(toMap(CourseSection::sectionId, identity()));
        Map<Integer, List<CourseSection>> sectionsByProfessorId =
                teachingHistoryRepository.findBySectionIdIn(semesterSectionsById.keySet()).stream()
                        .collect(groupingBy(
                                TeachingHistory::professorId,
                                mapping(teaching -> semesterSectionsById.get(teaching.sectionId()), toList())));
        Map<Integer, Course> semesterCoursesById = courseRepository
                .findAllById(semesterSectionsById.values().stream()
                        .map(CourseSection::courseId)
                        .collect(toSet()))
                .stream()
                .collect(toMap(Course::courseId, identity()));
        String observedDepartmentCode = departmentRepository
                .findById(departmentId)
                .map(Department::departmentCode)
                .orElse(null);

        List<ObserverCandidate> candidates = new ArrayList<>();
        for (Professor professor : professorRepository.findAllById(sectionsByProfessorId.keySet())) {
            List<CourseSection> taught = sectionsByProfessorId.get(professor.professorId());
            boolean eligible = !professor.professorId().equals(observed.professorId())
                    && Boolean.TRUE.equals(professor.activeStatus())
                    && departmentId.equals(professor.departmentId())
                    && taught.stream()
                            .noneMatch(other -> other.sectionId().equals(observedSection.sectionId())
                                    || SectionSchedules.conflict(other, observedSection));
            if (!eligible) {
                continue;
            }
            taught.stream()
                    .map(other -> semesterCoursesById.get(other.courseId()))
                    .filter(other -> departmentId.equals(other.departmentId()))
                    .flatMapToInt(other -> courseLevel(other.courseNumber()).stream())
                    .filter(level -> level >= observedLevel)
                    .max()
                    .ifPresent(level -> candidates.add(new ObserverCandidate(
                            professor.professorId(),
                            professor.firstName(),
                            professor.lastName(),
                            professor.email(),
                            observedDepartmentCode,
                            level)));
        }

        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(MAX_CANDIDATES)
                .sorted(comparing(ObserverCandidate::lastName)
                        .thenComparing(ObserverCandidate::firstName)
                        .thenComparing(ObserverCandidate::professorId))
                .toList();
    }

    /** Returns the level of a numeric course number (4348 is level 4000), or nothing if it is not numeric. */
    static OptionalInt courseLevel(String courseNumber) {
        if (courseNumber == null
                || !NUMERIC_COURSE_NUMBER.matcher(courseNumber.strip()).matches()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(courseNumber.strip()) / 1000 * 1000);
    }

    private static String requireText(String value, String parameter) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(parameter + " must not be blank");
        }
        return value.strip();
    }

    private static String normalizeName(String name) {
        return WHITESPACE.matcher(name.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
