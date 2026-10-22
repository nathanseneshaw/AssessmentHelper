package edu.utdallas.cs4485.assessmenthelper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.utdallas.cs4485.assessmenthelper.entity.Course;
import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.entity.Department;
import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.entity.School;
import edu.utdallas.cs4485.assessmenthelper.entity.Semester;
import edu.utdallas.cs4485.assessmenthelper.entity.TeachingHistory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * The demo rows from data.sql are in the database too, but they belong to the CS department and their own semesters,
 * so every test here only sees the professors it creates in the TEST department.
 */
@DataJdbcTest
@Import(ObserverCandidateService.class)
class ObserverCandidateServiceTests {

    @Autowired
    private ObserverCandidateService service;

    @Autowired
    private JdbcAggregateTemplate db;

    private Department department;
    private Semester fall;
    private Semester spring;
    private Course course1337;
    private Course course2336;
    private Course course3345;
    private Course course4348;
    private Course course6301;
    private Professor observee;
    private int nextSectionNumber = 100;

    /** The observee teaches TEST 3345.001 on TR 10:00-11:15 in the fall. */
    @BeforeEach
    void createObservee() {
        School school = db.insert(new School(null, "Test School"));
        department = db.insert(new Department(null, school.schoolId(), "Test Department", "TEST"));
        // The fall semester is inserted first, so it has the lower id even though it starts later.
        fall = db.insert(new Semester(null, "Fall", 2026, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 11)));
        spring = db.insert(new Semester(null, "Spring", 2026, LocalDate.of(2026, 1, 12), LocalDate.of(2026, 5, 8)));
        course1337 = course(department, "1337");
        course2336 = course(department, "2336");
        course3345 = course(department, "3345");
        course4348 = course(department, "4348");
        course6301 = course(department, "6301");
        observee = professor("Olivia", "Observee");
        teach(observee, course3345, fall, "001", "TR", "10:00", "11:15");
    }

    @Test
    void nobodyElseTeachingMeansNoCandidates() {
        assertThat(candidates()).isEmpty();
    }

    @Test
    void sameAndHigherLevelProfessorsAreCandidates() {
        Professor same = professor("Sam", "Same");
        teach(same, course3345, fall, "MW", "13:00", "14:15");
        Professor higher = professor("Hannah", "Higher");
        teach(higher, course6301, fall, "MW", "13:00", "14:15");

        assertThat(candidates())
                .containsExactly(
                        new ObserverCandidate(
                                higher.professorId(), "Hannah", "Higher", "hannah.higher@test.edu", "TEST", 6000),
                        new ObserverCandidate(same.professorId(), "Sam", "Same", "sam.same@test.edu", "TEST", 3000));
    }

    @Test
    void lowerLevelOnlyProfessorIsNotACandidate() {
        Professor lower = professor("Larry", "Lower");
        teach(lower, course1337, fall, "MW", "13:00", "14:15");
        teach(lower, course2336, fall, "MW", "14:30", "15:45");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void courseLevelIsTheHighestLevelTheCandidateTeaches() {
        Professor mixed = professor("Max", "Mixed");
        teach(mixed, course1337, fall, "MW", "08:00", "08:50");
        teach(mixed, course4348, fall, "MW", "09:00", "09:50");
        teach(mixed, course6301, fall, "MW", "13:00", "14:15");

        assertThat(candidates())
                .singleElement()
                .extracting(ObserverCandidate::courseLevel)
                .isEqualTo(6000);
    }

    @Test
    void nonNumericCourseNumbersDoNotQualifyAnyone() {
        Course research = db.insert(
                new Course(null, department.departmentId(), "TEST", "4V98", "Undergraduate Research", "UNDERGRADUATE"));
        Professor researcher = professor("Rita", "Research");
        teach(researcher, research, fall, "MW", "13:00", "14:15");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void professorWithAnOverlappingSectionOnASharedDayIsNotACandidate() {
        Professor overlapping = professor("Olga", "Overlap");
        teach(overlapping, course4348, fall, "TR", "11:00", "12:15");
        Professor tuesday = professor("Tess", "Tuesday");
        teach(tuesday, course4348, fall, "MTW", "09:30", "10:30");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void anyConflictingSectionRulesTheProfessorOut() {
        // The 4348 section would qualify them, but their 1337 section meets during the observed section.
        Professor conflicted = professor("Connie", "Conflict");
        teach(conflicted, course4348, fall, "MW", "13:00", "14:15");
        teach(conflicted, course1337, fall, "TR", "10:00", "10:50");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void sameTimeOnOtherDaysIsNoConflict() {
        Professor mondays = professor("Mona", "Monday");
        teach(mondays, course3345, fall, "MW", "10:00", "11:15");

        assertThat(candidateIds()).containsExactly(mondays.professorId());
    }

    @Test
    void backToBackSectionsAreNoConflict() {
        Professor after = professor("Andy", "After");
        teach(after, course4348, fall, "TR", "11:15", "12:30");
        Professor before = professor("Bella", "Before");
        teach(before, course4348, fall, "TR", "08:45", "10:00");

        assertThat(candidateIds()).containsExactly(after.professorId(), before.professorId());
    }

    @Test
    void sectionWithoutAScheduleIsNoConflict() {
        Professor online = professor("Olive", "Online");
        teach(online, course4348, fall, null, null, null);

        assertThat(candidateIds()).containsExactly(online.professorId());
    }

    @Test
    void professorsOutsideTheDepartmentAreNotCandidates() {
        // Same school, but a different department.
        Department otherDepartment =
                db.insert(new Department(null, department.schoolId(), "Other Department", "OTHER"));
        // Teaches a higher level course of the observed department, but belongs to another department.
        Professor outsider = db.insert(new Professor(
                null, otherDepartment.departmentId(), null, "Otto", "Outsider", "otto@test.edu", null, true));
        teach(outsider, course6301, fall, "MW", "13:00", "14:15");
        // Belongs to the observed department, but only teaches another department's course.
        Professor crossover = professor("Cris", "Crossover");
        teach(crossover, course(otherDepartment, "4000"), fall, "MW", "13:00", "14:15");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void inactiveProfessorIsNotACandidate() {
        Professor retired = db.insert(
                new Professor(null, department.departmentId(), null, "Rex", "Retired", "rex@test.edu", null, false));
        teach(retired, course4348, fall, "MW", "13:00", "14:15");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void observeeIsNotTheirOwnCandidate() {
        teach(observee, course4348, fall, "MW", "13:00", "14:15");

        assertThat(candidates()).isEmpty();
    }

    @Test
    void coInstructorOfTheObservedSectionIsNotACandidate() {
        CourseSection online = teach(observee, course4348, fall, "501", null, null, null);
        Professor coInstructor = professor("Cody", "CoInstructor");
        db.insert(new TeachingHistory(null, coInstructor.professorId(), online.sectionId()));
        Professor otherOnline = professor("Olive", "Online");
        teach(otherOnline, course4348, fall, null, null, null);

        assertThat(service.findCandidates("4348", "501", "Olivia Observee"))
                .extracting(ObserverCandidate::professorId)
                .containsExactly(otherOnline.professorId());
    }

    @Test
    void sectionsInOtherSemestersAreIgnored() {
        Professor busyInSpring = professor("Fiona", "Fall");
        teach(busyInSpring, course4348, fall, "MW", "13:00", "14:15");
        teach(busyInSpring, course3345, spring, "TR", "10:00", "11:15");
        Professor springOnly = professor("Sean", "Spring");
        teach(springOnly, course6301, spring, "MW", "13:00", "14:15");

        assertThat(candidateIds()).containsExactly(busyInSpring.professorId());
    }

    @Test
    void exactlyFiveCandidatesAreAllReturned() {
        Set<Integer> eligibleIds = createEligibleProfessors(ObserverCandidateService.MAX_CANDIDATES);

        assertThat(candidateIds()).containsExactlyInAnyOrderElementsOf(eligibleIds);
    }

    @Test
    void moreThanFiveCandidatesAreSampledAtRandom() {
        Set<Integer> eligibleIds = createEligibleProfessors(7);

        Set<Set<Integer>> picks = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            List<Integer> ids = candidateIds();
            assertThat(ids).hasSize(5).doesNotHaveDuplicates().isSubsetOf(eligibleIds);
            picks.add(Set.copyOf(ids));
        }
        // There are 21 ways to pick 5 of 7, so 20 identical picks in a row would be a 1 in 10^25 coincidence.
        assertThat(picks).hasSizeGreaterThan(1);
    }

    @Test
    void latestSemesterWinsWhenTheObserveeTaughtTheSectionBefore() {
        // Last spring the observed section met on MW instead of TR.
        teach(observee, course3345, spring, "001", "MW", "10:00", "11:15");
        Professor fallTeacher = professor("Fran", "Fall");
        teach(fallTeacher, course4348, fall, "MW", "10:00", "11:15");
        Professor springTeacher = professor("Sue", "Spring");
        teach(springTeacher, course4348, spring, "TR", "10:00", "11:15");

        assertThat(candidateIds()).containsExactly(fallTeacher.professorId());
    }

    @Test
    void courseNumberMayStartWithTheDepartmentCode() {
        Professor higher = professor("Hannah", "Higher");
        teach(higher, course4348, fall, "MW", "13:00", "14:15");
        List<ObserverCandidate> expected = candidates();

        assertThat(expected).isNotEmpty();
        assertThat(service.findCandidates("TEST 3345", "001", "Olivia Observee"))
                .isEqualTo(expected);
        assertThat(service.findCandidates("test3345", "001", "Olivia Observee")).isEqualTo(expected);
        assertThat(service.findCandidates("  Test   3345 ", " 001 ", "Olivia Observee"))
                .isEqualTo(expected);
    }

    @Test
    void departmentCodeMustMatchTheCourse() {
        // CS 3345.001 exists in the demo data, but Olivia teaches TEST 3345.001.
        assertThatThrownBy(() -> service.findCandidates("CS 3345", "001", "Olivia Observee"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.findCandidates("NOPE 3345", "001", "Olivia Observee"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No course NOPE 3345");
    }

    @Test
    void professorNameIgnoresCaseAndExtraWhitespace() {
        Professor higher = professor("Hannah", "Higher");
        teach(higher, course4348, fall, "MW", "13:00", "14:15");

        assertThat(service.findCandidates("3345", "001", "  olivia   OBSERVEE "))
                .extracting(ObserverCandidate::professorId)
                .containsExactly(higher.professorId());
    }

    @Test
    void unknownCourseThrowsNotFound() {
        assertThatThrownBy(() -> service.findCandidates("9999", "001", "Olivia Observee"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No course 9999");
    }

    @Test
    void unknownSectionThrowsNotFound() {
        assertThatThrownBy(() -> service.findCandidates("TEST 3345", "999", "Olivia Observee"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No section 999 of course TEST 3345");
    }

    @Test
    void professorWhoDoesNotTeachTheSectionThrowsNotFound() {
        professor("Nora", "Nobody");

        assertThatThrownBy(() -> service.findCandidates("3345", "001", "Nora Nobody"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No professor named Nora Nobody teaches section 001 of course 3345");
    }

    @Test
    void sectionWithoutAnyTeacherThrowsNotFound() {
        db.insert(new CourseSection(null, course4348.courseId(), fall.semesterId(), "777", null, null, null, null));

        assertThatThrownBy(() -> service.findCandidates("TEST 4348", "777", "Olivia Observee"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void blankArgumentsAreInvalid() {
        assertThatThrownBy(() -> service.findCandidates(" ", "001", "Olivia Observee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("courseNumber must not be blank");
        assertThatThrownBy(() -> service.findCandidates("3345", "", "Olivia Observee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sectionNumber must not be blank");
        assertThatThrownBy(() -> service.findCandidates("3345", "001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("professorName must not be blank");
    }

    @Test
    void nonNumericCourseNumberIsInvalid() {
        for (String courseNumber : List.of("abc", "CS 4V98", "CS 3345 001", "3345A", "1234567890")) {
            assertThatThrownBy(() -> service.findCandidates(courseNumber, "001", "Olivia Observee"))
                    .as(courseNumber)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'" + courseNumber + "'");
        }
    }

    private List<ObserverCandidate> candidates() {
        return service.findCandidates("3345", "001", "Olivia Observee");
    }

    private List<Integer> candidateIds() {
        return candidates().stream().map(ObserverCandidate::professorId).toList();
    }

    private Set<Integer> createEligibleProfessors(int count) {
        Set<Integer> ids = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            Professor professor = professor("Candidate", "Number" + i);
            teach(professor, course4348, fall, "MW", "13:00", "14:15");
            ids.add(professor.professorId());
        }
        return ids;
    }

    private Course course(Department owner, String courseNumber) {
        return db.insert(new Course(
                null,
                owner.departmentId(),
                owner.departmentCode(),
                courseNumber,
                "Course " + courseNumber,
                "UNDERGRADUATE"));
    }

    private Professor professor(String firstName, String lastName) {
        String email = (firstName + "." + lastName + "@test.edu").toLowerCase(Locale.ROOT);
        return db.insert(new Professor(null, department.departmentId(), null, firstName, lastName, email, null, true));
    }

    private CourseSection teach(
            Professor professor, Course course, Semester semester, String meetingDays, String start, String end) {
        return teach(professor, course, semester, String.valueOf(nextSectionNumber++), meetingDays, start, end);
    }

    private CourseSection teach(
            Professor professor,
            Course course,
            Semester semester,
            String sectionNumber,
            String meetingDays,
            String start,
            String end) {
        CourseSection section = db.insert(new CourseSection(
                null,
                course.courseId(),
                semester.semesterId(),
                sectionNumber,
                meetingDays,
                start == null ? null : LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end),
                null));
        db.insert(new TeachingHistory(null, professor.professorId(), section.sectionId()));
        return section;
    }
}
